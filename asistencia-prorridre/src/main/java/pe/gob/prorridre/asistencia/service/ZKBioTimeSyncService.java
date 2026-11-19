package pe.gob.prorridre.asistencia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.SincronizacionMaestrosResultDto;
import pe.gob.prorridre.asistencia.model.Departamento;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Servicio de Sincronización de Datos Maestros desde ZKTeco / ZKBio Time 8.0.8.
 *
 * Responsabilidades:
 * 1. Descarga y sincroniza la estructura de Departamentos/Metas con árbol recursivo.
 * 2. Descarga y sincroniza la nómina de Empleados con nombres completos, cargos,
 *    fechas de contratación y asignación departamental real.
 * 3. Enriquece retroactivamente los registros de asistencia históricos que llegaron con
 *    nombres vacíos o departamentos desorganizados.
 */
@Service
public class ZKBioTimeSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZKBioTimeSyncService.class);

    private static final String LOGIN_PATH = "/jwt-api-token-auth/";
    private static final String DEPARTMENTS_PATH = "/personnel/api/departments/";
    private static final String EMPLOYEES_PATH = "/personnel/api/employees/";

    private final DepartamentoRepository departamentoRepo;
    private final EmpleadoRepository empleadoRepo;
    private final RegistroAsistenciaRepository registroRepo;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${zkbio.url:http://192.168.1.101:8085}")
    private String zkBioUrl;

    @Value("${zkbio.usuario:${ZK_USER:admin}}")
    private String zkUser;

    @Value("${zkbio.clave:${ZK_PASS:prorridre2026}}")
    private String zkPass;

    private volatile String tokenCached = null;

    public ZKBioTimeSyncService(DepartamentoRepository departamentoRepo,
                                EmpleadoRepository empleadoRepo,
                                RegistroAsistenciaRepository registroRepo,
                                ObjectMapper objectMapper) {
        this.departamentoRepo = departamentoRepo;
        this.empleadoRepo = empleadoRepo;
        this.registroRepo = registroRepo;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Ejecuta la sincronización completa de maestros (Departamentos + Empleados)
     * y actualiza retroactivamente los registros de asistencia.
     */
    @Transactional
    public SincronizacionMaestrosResultDto sincronizarMaestros() {
        long inicio = System.currentTimeMillis();
        log.info("[Maestros-Sync] Iniciando sincronización de maestros desde ZKBio Time en {}", zkBioUrl);
        SincronizacionMaestrosResultDto resultado = new SincronizacionMaestrosResultDto();

        try {
            String token = obtenerTokenJwt();

            // 1. Sincronizar Departamentos respetando jerarquía
            int[] deptsCount = sincronizarDepartamentos(token);
            resultado.setTotalDepartamentos(deptsCount[0]);
            resultado.setDepartamentosInsertados(deptsCount[1]);
            resultado.setDepartamentosActualizados(deptsCount[2]);

            // 2. Sincronizar Empleados
            int[] empsCount = sincronizarEmpleados(token);
            resultado.setTotalEmpleados(empsCount[0]);
            resultado.setEmpleadosInsertados(empsCount[1]);
            resultado.setEmpleadosActualizados(empsCount[2]);
            resultado.setEmpleadosDesconocidos(empsCount[3]);

            // 3. Enriquecer retroactivamente registros de asistencia con nombres reales
            int registrosEnriquecidos = enriquecerRegistrosAsistenciaHistoricos();

            resultado.setExitoso(true);
            resultado.setDuracionMs(System.currentTimeMillis() - inicio);
            resultado.setMensaje(String.format(
                    "Sincronización de maestros exitosa: %d departamentos (%d nuevos, %d actualizados) y " +
                    "%d empleados (%d nuevos, %d actualizados, %d registros de asistencia enriquecidos con nombres reales).",
                    deptsCount[0], deptsCount[1], deptsCount[2],
                    empsCount[0], empsCount[1], empsCount[2], registrosEnriquecidos
            ));
            log.info("[Maestros-Sync] {}", resultado.getMensaje());
            return resultado;

        } catch (Exception ex) {
            log.error("[Maestros-Sync] Error durante sincronización de maestros: {}", ex.getMessage(), ex);
            resultado.setExitoso(false);
            resultado.setMensaje("Error al sincronizar datos maestros: " + ex.getMessage());
            resultado.setDuracionMs(System.currentTimeMillis() - inicio);
            return resultado;
        }
    }

    /**
     * Sincroniza la tabla de Departamentos en 2 pasadas:
     * Pasada 1: Crear o actualizar todos los nodos con sus nombres y códigos.
     * Pasada 2: Vincular las relaciones recursivas parent_id.
     */
    private int[] sincronizarDepartamentos(String token) {
        log.info("[Maestros-Sync] Consultando departamentos en {}", DEPARTMENTS_PATH);
        List<JsonNode> deptNodes = obtenerListaPaginada(token, DEPARTMENTS_PATH, "departments");

        int total = 0, nuevos = 0, actualizados = 0;
        Map<Long, Long> mapaPadres = new HashMap<>();

        // Pasada 1: Guardar entidades base
        for (JsonNode node : deptNodes) {
            if (!node.has("id")) continue;
            Long id = node.get("id").asLong();
            String codigo = node.has("dept_code") ? node.get("dept_code").asText() : String.valueOf(id);
            String nombre = node.has("dept_name") ? node.get("dept_name").asText().trim() : "DEPARTAMENTO " + id;

            // Extraer ID del padre
            if (node.has("parent_dept") && !node.get("parent_dept").isNull()) {
                JsonNode parentNode = node.get("parent_dept");
                if (parentNode.isObject() && parentNode.has("id")) {
                    mapaPadres.put(id, parentNode.get("id").asLong());
                } else if (parentNode.isNumber()) {
                    mapaPadres.put(id, parentNode.asLong());
                }
            }

            Optional<Departamento> existente = departamentoRepo.findById(id);
            Departamento d;
            if (existente.isPresent()) {
                d = existente.get();
                d.setCodigo(codigo);
                d.setNombre(nombre);
                actualizados++;
            } else {
                d = new Departamento(id, codigo, nombre, null);
                nuevos++;
            }
            d.setActivo(true);
            departamentoRepo.save(d);
            total++;
        }
        departamentoRepo.flush();

        // Pasada 2: Establecer relaciones de jerarquía padre-hijo
        for (Map.Entry<Long, Long> entry : mapaPadres.entrySet()) {
            Long hijoId = entry.getKey();
            Long padreId = entry.getValue();

            Optional<Departamento> hijoOpt = departamentoRepo.findById(hijoId);
            Optional<Departamento> padreOpt = departamentoRepo.findById(padreId);

            if (hijoOpt.isPresent() && padreOpt.isPresent()) {
                Departamento hijo = hijoOpt.get();
                hijo.setParent(padreOpt.get());
                departamentoRepo.save(hijo);
            }
        }
        departamentoRepo.flush();

        log.info("[Maestros-Sync] Departamentos procesados: {} ({} nuevos, {} actualizados)", total, nuevos, actualizados);
        return new int[]{total, nuevos, actualizados};
    }

    /**
     * Sincroniza la nómina de Empleados desde ZKBio Time asociando a cada uno
     * con su Departamento real, cargo y fecha de ingreso.
     */
    private int[] sincronizarEmpleados(String token) {
        log.info("[Maestros-Sync] Consultando empleados en {}", EMPLOYEES_PATH);
        List<JsonNode> empNodes = obtenerListaPaginada(token, EMPLOYEES_PATH, "employees");

        int total = 0, nuevos = 0, actualizados = 0, desconocidos = 0;

        for (JsonNode node : empNodes) {
            String empCode = node.has("emp_code") ? node.get("emp_code").asText().trim() : "";
            if (empCode.isEmpty() && node.has("id")) {
                empCode = String.valueOf(node.get("id").asLong());
            }
            if (empCode.isEmpty()) continue;

            String nombres = node.has("first_name") && !node.get("first_name").isNull() ? node.get("first_name").asText().trim() : "";
            String apellidos = node.has("last_name") && !node.get("last_name").isNull() ? node.get("last_name").asText().trim() : "";

            // Cargo / Posición
            String cargo = "";
            if (node.has("position") && !node.get("position").isNull()) {
                JsonNode posNode = node.get("position");
                if (posNode.isObject() && posNode.has("position_name")) {
                    cargo = posNode.get("position_name").asText().trim();
                } else if (posNode.isTextual()) {
                    cargo = posNode.asText().trim();
                }
            }

            // Fecha de Contratación
            LocalDate fechaContratacion = null;
            if (node.has("hire_date") && !node.get("hire_date").isNull()) {
                String hireDateStr = node.get("hire_date").asText().trim();
                try {
                    fechaContratacion = LocalDate.parse(hireDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (Exception ignored) {}
            }

            // Empresa
            String empresa = "PRORRIDRE PUNO";
            if (node.has("company") && !node.get("company").isNull()) {
                JsonNode compNode = node.get("company");
                if (compNode.isObject() && compNode.has("company_name")) {
                    empresa = compNode.get("company_name").asText().trim();
                }
            }

            // Departamento
            Departamento depto = null;
            if (node.has("department") && !node.get("department").isNull()) {
                JsonNode deptNode = node.get("department");
                Long deptId = null;
                if (deptNode.isObject() && deptNode.has("id")) {
                    deptId = deptNode.get("id").asLong();
                } else if (deptNode.isNumber()) {
                    deptId = deptNode.asLong();
                }

                if (deptId != null) {
                    depto = departamentoRepo.findById(deptId).orElse(null);
                }
            }

            // Activo
            boolean activo = true;
            if (node.has("attemployee") && !node.get("attemployee").isNull()) {
                JsonNode att = node.get("attemployee");
                if (att.has("enable_attendance")) {
                    activo = att.get("enable_attendance").asBoolean(true);
                }
            }

            Optional<Empleado> existenteOpt = empleadoRepo.findById(empCode);
            Empleado emp;
            if (existenteOpt.isPresent()) {
                emp = existenteOpt.get();
                emp.setNombres(nombres);
                emp.setApellidos(apellidos);
                emp.setCargo(cargo);
                emp.setFechaContratacion(fechaContratacion);
                emp.setEmpresa(empresa);
                emp.setDepartamento(depto);
                emp.setActivo(activo);
                actualizados++;
            } else {
                emp = new Empleado(empCode, nombres, apellidos, cargo, fechaContratacion, depto);
                emp.setEmpresa(empresa);
                emp.setActivo(activo);
                nuevos++;
            }

            if (emp.isDesconocido()) {
                desconocidos++;
            }

            empleadoRepo.save(emp);
            total++;
        }
        empleadoRepo.flush();

        log.info("[Maestros-Sync] Empleados procesados: {} ({} nuevos, {} actualizados, {} desconocidos)",
                total, nuevos, actualizados, desconocidos);
        return new int[]{total, nuevos, actualizados, desconocidos};
    }

    /**
     * Enriquece los registros de asistencia ya calculados en la base de datos local
     * reemplazando nombres/apellidos vacíos con los datos maestros canónicos.
     */
    public int enriquecerRegistrosAsistenciaHistoricos() {
        List<RegistroAsistencia> registros = registroRepo.findAll();
        Map<String, Empleado> mapaEmpleados = new HashMap<>();
        for (Empleado e : empleadoRepo.findAll()) {
            mapaEmpleados.put(e.getEmpId(), e);
        }

        int actualizados = 0;
        for (RegistroAsistencia r : registros) {
            String empId = r.getEmpId();
            if (empId == null) continue;

            Empleado emp = mapaEmpleados.get(empId);
            if (emp != null) {
                boolean modificado = false;
                // Si el nombre o apellido del registro estaba vacío o era temporal, actualizarlo
                if (r.getNombres() == null || r.getNombres().trim().isEmpty() || r.getNombres().startsWith("EMP-")) {
                    r.setNombres(emp.getNombres());
                    modificado = true;
                }
                if (r.getApellidos() == null || r.getApellidos().trim().isEmpty()) {
                    r.setApellidos(emp.getApellidos());
                    modificado = true;
                }
                if (emp.getDepartamentoNombre() != null && !emp.getDepartamentoNombre().isEmpty()) {
                    // Actualizar si el departamento original era un valor genérico
                    if (r.getDepartamento() == null || r.getDepartamento().equalsIgnoreCase("PRORRIDRE PUNO")
                            || r.getDepartamento().equalsIgnoreCase("GENERAL")
                            || r.getDepartamento().equalsIgnoreCase("SIN DEPARTAMENTO")) {
                        r.setDepartamento(emp.getDepartamentoNombre());
                        modificado = true;
                    }
                }
                if (modificado) {
                    registroRepo.save(r);
                    actualizados++;
                }
            }
        }
        log.info("[Maestros-Sync] Enriquecimiento completado: {} registros de asistencia actualizados con datos maestros.", actualizados);
        return actualizados;
    }

    /**
     * Consume un endpoint REST paginado de ZKBio Time y recopila todos sus elementos.
     */
    private List<JsonNode> obtenerListaPaginada(String token, String endpointPath, String nombreRecurso) {
        List<JsonNode> resultado = new ArrayList<>();
        int pagina = 1;
        int pageSize = 200;

        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(zkBioUrl)
                    .path(endpointPath)
                    .queryParam("page", pagina)
                    .queryParam("page_size", pageSize)
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "JWT " + token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode dataNode = root.get("data");

                    if (dataNode != null && dataNode.isArray()) {
                        for (JsonNode item : dataNode) {
                            resultado.add(item);
                        }
                    }

                    // Verificar si hay más páginas
                    JsonNode nextNode = root.get("next");
                    if (nextNode == null || nextNode.isNull() || nextNode.asText().isEmpty() || "null".equals(nextNode.asText())) {
                        break;
                    }
                    pagina++;
                } else {
                    break;
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                log.warn("[Maestros-Sync] Token expirado en página {}. Renovando token...", pagina);
                tokenCached = null;
                token = obtenerTokenJwt();
            } catch (Exception ex) {
                log.error("[Maestros-Sync] Error al consultar página {} de {}: {}", pagina, nombreRecurso, ex.getMessage());
                break;
            }
        }

        return resultado;
    }

    /**
     * Obtiene o renueva el token JWT contra ZKBio Time.
     */
    private synchronized String obtenerTokenJwt() {
        if (tokenCached != null && !tokenCached.trim().isEmpty()) {
            return tokenCached;
        }

        URI authUri = UriComponentsBuilder.fromHttpUrl(zkBioUrl)
                .path(LOGIN_PATH)
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> credenciales = new HashMap<>();
        credenciales.put("username", zkUser);
        credenciales.put("password", zkPass);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(credenciales, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(authUri, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("token")) {
                    this.tokenCached = root.get("token").asText();
                    return this.tokenCached;
                }
            }
        } catch (Exception ex) {
            log.error("[Maestros-Sync] Error autenticando con ZKBio Time: {}", ex.getMessage());
            throw new RuntimeException("Fallo de autenticación con ZKBio Time en " + zkBioUrl + ": " + ex.getMessage(), ex);
        }

        throw new RuntimeException("No se recibió token en la respuesta de autenticación de ZKBio Time.");
    }
}

