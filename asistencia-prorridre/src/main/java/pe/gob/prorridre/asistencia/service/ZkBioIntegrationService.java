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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pe.gob.prorridre.asistencia.domain.entity.Marcacion;
import pe.gob.prorridre.asistencia.domain.repository.MarcacionRepository;
import pe.gob.prorridre.asistencia.dto.ImportResultDto;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Servicio de Integración en Tiempo Real con ZKTeco / ZKBio Time 8.0.8.
 *
 * Responsabilidades:
 * 1. Autenticación REST: Obtiene y renueva el token JWT en /jwt-api-token-auth/
 * 2. Extracción Incremental: Consulta /iclock/api/transactions/ trayendo únicamente
 *    las transacciones posteriores al último punch_time almacenado en H2.
 * 3. Mapeo e Inserción: Transforma el payload JSON en entidades Marcacion y las persiste
 *    en lote en H2 evitando duplicados de clave primaria.
 * 4. Disparo del Motor Matemático: Recalcula automáticamente las 18 columnas (A:R)
 *    de asistencia y planillas para reflejar los nuevos datos en el Dashboard.
 */
@Service
public class ZkBioIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ZkBioIntegrationService.class);

    private static final String LOGIN_PATH = "/jwt-api-token-auth/";
    private static final String TRANSACTIONS_PATH = "/iclock/api/transactions/";
    private static final DateTimeFormatter FMT_ZKBIO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MarcacionRepository marcacionRepo;
    private final ExcelCsvImportService excelCsvImportService;
    private final ZKBioTimeSyncService zkBioTimeSyncService;
    private final pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository empleadoRepo;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${zkbio.url:http://192.168.1.101:8085}")
    private String zkBioUrl;

    @Value("${zkbio.usuario:${ZK_USER:admin}}")
    private String zkUser;

    @Value("${zkbio.clave:${ZK_PASS:prorridre2026}}")
    private String zkPass;

    @Value("${zkbio.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${zkbio.page-size:1000}")
    private int pageSize;

    private volatile String tokenActual = null;

    public ZkBioIntegrationService(MarcacionRepository marcacionRepo,
                                  ExcelCsvImportService excelCsvImportService,
                                  ZKBioTimeSyncService zkBioTimeSyncService,
                                  pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository empleadoRepo,
                                  ObjectMapper objectMapper) {
        this.marcacionRepo = marcacionRepo;
        this.excelCsvImportService = excelCsvImportService;
        this.zkBioTimeSyncService = zkBioTimeSyncService;
        this.empleadoRepo = empleadoRepo;
        this.objectMapper = objectMapper;

        // Configuración de RestTemplate con timeouts estrictos para no bloquear hilos
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Punto de entrada principal ejecutado al presionar "Sincronizar BD".
     * Extrae las marcaciones vivas desde ZKBio Time, las guarda en H2 y recalcula la asistencia.
     */
    public ImportResultDto sincronizarMarcacionesEnVivo() {
        log.info("[ZKBio-Live] Iniciando sincronización en vivo con ZKBio Time en {}", zkBioUrl);

        try {
            // 0. Sincronizar maestros si el catálogo local está vacío o incompleto
            if (empleadoRepo.count() < 10) {
                log.info("[ZKBio-Live] Catálogo maestro local con pocos registros. Sincronizando maestros primero...");
                try {
                    zkBioTimeSyncService.sincronizarMaestros();
                } catch (Exception exM) {
                    log.warn("[ZKBio-Live] Aviso al sincronizar maestros: {}", exM.getMessage());
                }
            }

            // 1. Obtener Token JWT
            String token = obtenerTokenJwt();

            // 2. Determinar ventana de tiempo incremental
            LocalDateTime desde = calcularFechaInicioFiltro();
            LocalDateTime hasta = LocalDateTime.now();
            log.info("[ZKBio-Live] Ventana de extracción incremental: [{} → {}]", desde.format(FMT_ZKBIO), hasta.format(FMT_ZKBIO));

            // 3. Extraer transacciones paginadas desde la API de ZKBio Time
            List<JsonNode> transaccionesRaw = extraerTransaccionesPaginadas(token, desde, hasta);

            if (transaccionesRaw.isEmpty()) {
                log.info("[ZKBio-Live] ZKBio Time no reportó marcaciones nuevas en el período.");
                // Recalcular asistencia con los registros locales existentes
                return excelCsvImportService.sincronizarDesdeMarcaciones();
            }

            // 4. Mapear e insertar en la tabla MARCACION de H2
            int insertadas = procesarEInsertarMarcaciones(transaccionesRaw);
            log.info("[ZKBio-Live] Inserción completada: {} marcaciones nuevas almacenadas en H2.", insertadas);

            // 5. Disparar el motor matemático auditado sobre las marcaciones actualizadas
            ImportResultDto resultadoMotor = excelCsvImportService.sincronizarDesdeMarcaciones();
            resultadoMotor.setMensaje(String.format("ZKBio Time: %d marcaciones nuevas extraídas en vivo. %s",
                    insertadas, resultadoMotor.getMensaje()));

            return resultadoMotor;

        } catch (ResourceAccessException ex) {
            log.warn("[ZKBio-Live] No se pudo conectar al servidor ZKBio Time ({}): {}. Aplicando recálculo con datos locales.",
                    zkBioUrl, ex.getMessage());
            // Fallback elegante: si el reloj físico está apagado o en otra subred, procesa lo que ya está en H2
            ImportResultDto fallback = excelCsvImportService.sincronizarDesdeMarcaciones();
            fallback.setMensaje("Aviso: Servidor ZKBio Time (" + zkBioUrl + ") no alcanzable en red local. Se sincronizó con los registros locales de H2.");
            return fallback;

        } catch (Exception ex) {
            log.error("[ZKBio-Live] Error durante la sincronización con ZKBio Time: {}", ex.getMessage(), ex);
            return new ImportResultDto(false, "Error al sincronizar con ZKBio Time: " + ex.getMessage(), 0, 0, 0, 1);
        }
    }

    /**
     * Paso 1: Autenticación en ZKBio Time.
     * POST /jwt-api-token-auth/
     * Body: { "username": "admin", "password": "..." }
     */
    public synchronized String obtenerTokenJwt() {
        if (this.tokenActual != null) {
            return this.tokenActual;
        }

        String loginUrl = zkBioUrl + LOGIN_PATH;
        log.info("[ZKBio-Live] Autenticando contra ZKBio Time en {}", loginUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> credenciales = new HashMap<>();
        credenciales.put("username", zkUser);
        credenciales.put("password", zkPass);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(credenciales, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(loginUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("token")) {
                    this.tokenActual = root.get("token").asText();
                    log.info("[ZKBio-Live] Autenticación exitosa. Token JWT de sesión obtenido.");
                    return this.tokenActual;
                }
            }
            throw new IllegalStateException("Respuesta de login no contiene campo 'token'");

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            log.error("[ZKBio-Live] Credenciales inválidas para ZKBio Time (usuario: {})", zkUser);
            throw new SecurityException("Credenciales inválidas para ZKBio Time. Verifique ZK_USER y ZK_PASS.", ex);
        } catch (Exception ex) {
            log.error("[ZKBio-Live] Falla de autenticación en {}: {}", loginUrl, ex.getMessage());
            throw new RuntimeException("Falla al autenticar en ZKBio Time: " + ex.getMessage(), ex);
        }
    }

    /**
     * Paso 2: Consulta incremental de transacciones.
     * GET /iclock/api/transactions/?start_time=...&end_time=...&page=...
     * Header: Authorization: JWT <token>
     */
    private List<JsonNode> extraerTransaccionesPaginadas(String token, LocalDateTime desde, LocalDateTime hasta) {
        List<JsonNode> transacciones = new ArrayList<>();
        int pagina = 1;
        boolean hayMas = true;

        String strDesde = desde.format(FMT_ZKBIO);
        String strHasta = hasta.format(FMT_ZKBIO);

        while (hayMas) {
            URI uri = UriComponentsBuilder.fromHttpUrl(zkBioUrl + TRANSACTIONS_PATH)
                    .queryParam("start_time", strDesde)
                    .queryParam("end_time", strHasta)
                    .queryParam("page", pagina)
                    .queryParam("page_size", pageSize)
                    .build()
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "JWT " + token);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode dataArray = root.path("data");

                    if (dataArray.isArray() && dataArray.size() > 0) {
                        for (JsonNode item : dataArray) {
                            transacciones.add(item);
                        }
                        log.debug("[ZKBio-Live] Página {}: {} registros recibidos.", pagina, dataArray.size());
                    } else {
                        hayMas = false;
                        break;
                    }

                    // Evaluar campo "next" devuelto por ZKBio Time
                    JsonNode nextNode = root.path("next");
                    if (nextNode.isNull() || nextNode.asText().isEmpty() || nextNode.asText().equals("null")) {
                        hayMas = false;
                    } else {
                        pagina++;
                    }
                } else {
                    hayMas = false;
                }

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
                log.warn("[ZKBio-Live] Token JWT expirado durante la extracción. Renovando token...");
                this.tokenActual = null;
                token = obtenerTokenJwt();
                // Reintentar la misma página con el nuevo token
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                log.error("[ZKBio-Live] Error procesando JSON de página {}: {}", pagina, ex.getMessage());
                hayMas = false;
            }
        }

        return transacciones;
    }

    /**
     * Paso 3: Mapeo a entidad Marcacion y persistencia en H2.
     * Extrae: emp_code, punch_time, punch_state, verify_type, terminal_sn.
     */
    @Transactional
    public int procesarEInsertarMarcaciones(List<JsonNode> rawItems) {
        int insertadas = 0;
        int omitidasDuplicadas = 0;
        Set<Long> vistosEnLote = new HashSet<>();

        for (JsonNode item : rawItems) {
            Long id = item.path("id").isNumber() ? item.path("id").asLong() : null;
            if (id == null || vistosEnLote.contains(id)) {
                continue;
            }
            vistosEnLote.add(id);

            // Evitar duplicados de clave primaria
            if (marcacionRepo.existsById(id)) {
                omitidasDuplicadas++;
                continue;
            }

            String empCode = item.path("emp_code").asText("").trim();
            String firstName = item.path("first_name").asText("").trim();
            String lastName = item.path("last_name").asText("").trim();
            String punchState = item.path("punch_state").asText("0").trim();
            Integer verifyType = item.path("verify_type").isNumber() ? item.path("verify_type").asInt() : null;
            String terminalSn = item.path("terminal_sn").asText("").trim();

            LocalDateTime punchTime = parsearFechaHora(item.path("punch_time").asText(""));
            if (punchTime == null) continue;

            LocalDateTime uploadTime = parsearFechaHora(item.path("upload_time").asText(""));

            Marcacion m = new Marcacion();
            m.setId(id);
            m.setEmpCode(empCode);
            m.setFirstName(firstName);
            m.setLastName(lastName);
            m.setPunchTime(punchTime);
            m.setPunchState(punchState);
            m.setVerifyType(verifyType);
            m.setTerminalSn(terminalSn);
            m.setUploadTime(uploadTime);
            m.setRawJson(item.toString());

            try {
                marcacionRepo.save(m);
                insertadas++;
            } catch (Exception ex) {
                // Si el lector programado en segundo plano insertó este ID concurrentemente, se ignora
                omitidasDuplicadas++;
            }
        }

        try {
            marcacionRepo.flush();
        } catch (Exception ignored) {}

        log.info("[ZKBio-Live] Registros evaluados: {}. Insertados: {}. Duplicados ignorados: {}.",
                rawItems.size(), insertadas, omitidasDuplicadas);

        return insertadas;
    }

    /**
     * Calcula la fecha desde la cual solicitar marcaciones.
     * Toma el MAX(punch_time) en H2 y descuenta 1 día como ventana de seguridad
     * para absorber marcaciones que el reloj haya subido con retraso.
     */
    private LocalDateTime calcularFechaInicioFiltro() {
        return marcacionRepo.findMaxPunchTime()
                .map(maximo -> maximo.minusDays(1))
                .orElse(LocalDateTime.now().minusDays(30)); // 30 días si la BD estuviera completamente vacía
    }

    private LocalDateTime parsearFechaHora(String str) {
        if (str == null || str.trim().isEmpty() || str.equals("null")) return null;
        try {
            return LocalDateTime.parse(str.trim(), FMT_ZKBIO);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(str.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ex) {
                log.warn("[ZKBio-Live] No se pudo parsear fecha/hora: '{}'", str);
                return null;
            }
        }
    }
}
