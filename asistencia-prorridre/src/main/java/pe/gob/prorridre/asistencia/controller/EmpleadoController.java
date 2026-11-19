package pe.gob.prorridre.asistencia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoConfigFinancieraRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.dto.EmpleadoDto;
import pe.gob.prorridre.asistencia.model.Departamento;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.EmpleadoConfigFinanciera;
import pe.gob.prorridre.asistencia.service.ZKBioTimeSyncService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador REST para la gestión del Padrón Institucional de Empleados.
 * Permite altas, bajas (soft delete), cambios de cargo, reasignación de metas y ajuste de sueldos.
 */
@RestController
@RequestMapping("/api/v1/empleados")
@CrossOrigin(origins = "*")
public class EmpleadoController {

    private final EmpleadoRepository empleadoRepo;
    private final DepartamentoRepository departamentoRepo;
    private final EmpleadoConfigFinancieraRepository configRepo;
    private final ZKBioTimeSyncService syncService;

    public EmpleadoController(EmpleadoRepository empleadoRepo,
                              DepartamentoRepository departamentoRepo,
                              EmpleadoConfigFinancieraRepository configRepo,
                              ZKBioTimeSyncService syncService) {
        this.empleadoRepo = empleadoRepo;
        this.departamentoRepo = departamentoRepo;
        this.configRepo = configRepo;
        this.syncService = syncService;
    }

    /**
     * GET /api/v1/empleados
     * Lista todos los empleados del padrón con filtros opcionales.
     * Permitido para ADMIN y RRHH.
     */
    @GetMapping
    public ResponseEntity<List<EmpleadoDto>> listar(
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) Boolean activo) {

        List<Empleado> lista = empleadoRepo.findAll();

        // 1. Filtro por estado activo/inactivo
        if (activo != null) {
            lista = lista.stream()
                    .filter(e -> Objects.equals(e.getActivo(), activo))
                    .collect(Collectors.toList());
        }

        // 2. Filtro por departamento jerárquico
        if (departamento != null && !departamento.trim().isEmpty()) {
            Set<String> permitidos = departamentoRepo.obtenerNombresDescendientesDe(departamento.trim());
            lista = lista.stream()
                    .filter(e -> e.getDepartamentoNombre() != null && permitidos.contains(e.getDepartamentoNombre().trim().toUpperCase()))
                    .collect(Collectors.toList());
        }

        // 3. Filtro por búsqueda textual (ID, nombres, apellidos, cargo)
        if (buscar != null && !buscar.trim().isEmpty()) {
            String b = buscar.trim().toUpperCase();
            lista = lista.stream()
                    .filter(e -> e.getEmpId().toUpperCase().contains(b) ||
                                 e.getNombreCompleto().toUpperCase().contains(b) ||
                                 (e.getCargo() != null && e.getCargo().toUpperCase().contains(b)))
                    .collect(Collectors.toList());
        }

        // Cargar mapa de sueldos
        Map<String, BigDecimal> sueldos = configRepo.findAll().stream()
                .collect(Collectors.toMap(EmpleadoConfigFinanciera::getEmpId, EmpleadoConfigFinanciera::getSueldoMensual, (a, b) -> a));

        List<EmpleadoDto> dtos = lista.stream().map(e -> {
            EmpleadoDto dto = convertirADto(e);
            boolean esPracticante = (e.getDepartamentoNombre() != null && e.getDepartamentoNombre().toUpperCase().contains("PRACTICANTE")) ||
                                    (e.getCargo() != null && e.getCargo().toUpperCase().contains("PRACTICANTE"));
            BigDecimal sueldoGuardado = sueldos.get(e.getEmpId());
            BigDecimal sueldoFinal = esPracticante ? BigDecimal.ZERO : (sueldoGuardado != null ? sueldoGuardado : new BigDecimal("3000.00"));
            dto.setSueldoMensual(sueldoFinal);
            return dto;
        }).sorted(Comparator.comparing(EmpleadoDto::getNombreCompleto))
          .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/v1/empleados/{empId}
     * Detalle de un empleado específico con su configuración financiera.
     */
    @GetMapping("/{empId}")
    public ResponseEntity<EmpleadoDto> obtenerPorId(@PathVariable String empId) {
        return empleadoRepo.findById(empId)
                .map(e -> {
                    EmpleadoDto dto = convertirADto(e);
                    boolean esPracticante = (e.getDepartamentoNombre() != null && e.getDepartamentoNombre().toUpperCase().contains("PRACTICANTE")) ||
                                            (e.getCargo() != null && e.getCargo().toUpperCase().contains("PRACTICANTE"));
                    if (esPracticante) {
                        dto.setSueldoMensual(BigDecimal.ZERO);
                    } else {
                        configRepo.findByEmpId(empId).ifPresent(c -> dto.setSueldoMensual(c.getSueldoMensual()));
                    }
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/v1/empleados
     * Registra un nuevo empleado en el padrón institucional.
     * Restringido a ROLE_ADMIN.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> crearEmpleado(@RequestBody EmpleadoDto req) {
        if (req.getEmpId() == null || req.getEmpId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El ID o código biométrico es obligatorio."));
        }

        String cleanId = req.getEmpId().trim();
        if (empleadoRepo.existsById(cleanId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe un empleado registrado con el ID: " + cleanId));
        }

        Departamento depto = resolverDepartamento(req.getDepartamentoId(), req.getDepartamentoNombre());

        Empleado emp = new Empleado();
        emp.setEmpId(cleanId);
        emp.setNombres(req.getNombres() != null ? req.getNombres().trim() : "");
        emp.setApellidos(req.getApellidos() != null ? req.getApellidos().trim() : "");
        emp.setCargo(req.getCargo() != null ? req.getCargo().trim() : "Personal");
        emp.setFechaContratacion(req.getFechaContratacion() != null ? req.getFechaContratacion() : LocalDate.now());
        emp.setEmpresa(req.getEmpresa() != null ? req.getEmpresa().trim() : "PRORRIDRE PUNO");
        emp.setDepartamento(depto);
        emp.setActivo(req.getActivo() != null ? req.getActivo() : true);

        Empleado guardado = empleadoRepo.save(emp);

        // Guardar configuración financiera
        EmpleadoConfigFinanciera conf = configRepo.findByEmpId(cleanId)
                .orElse(new EmpleadoConfigFinanciera(cleanId));
        if (req.getSueldoMensual() != null) {
            conf.setSueldoMensual(req.getSueldoMensual());
        }
        conf.setCargo(emp.getCargo());
        configRepo.save(conf);

        EmpleadoDto resultado = convertirADto(guardado);
        resultado.setSueldoMensual(conf.getSueldoMensual());

        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    /**
     * PUT /api/v1/empleados/{empId}
     * Actualiza los datos maestros y salariales de un trabajador.
     * Restringido a ROLE_ADMIN.
     */
    @PutMapping("/{empId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> actualizarEmpleado(@PathVariable String empId, @RequestBody EmpleadoDto req) {
        Optional<Empleado> opt = empleadoRepo.findById(empId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Empleado emp = opt.get();
        if (req.getNombres() != null) emp.setNombres(req.getNombres().trim());
        if (req.getApellidos() != null) emp.setApellidos(req.getApellidos().trim());
        if (req.getCargo() != null) emp.setCargo(req.getCargo().trim());
        if (req.getFechaContratacion() != null) emp.setFechaContratacion(req.getFechaContratacion());
        if (req.getActivo() != null) emp.setActivo(req.getActivo());

        Departamento depto = resolverDepartamento(req.getDepartamentoId(), req.getDepartamentoNombre());
        if (depto != null) {
            emp.setDepartamento(depto);
        } else if (req.getDepartamentoNombre() != null) {
            emp.setDepartamentoNombre(req.getDepartamentoNombre().trim());
        }

        Empleado guardado = empleadoRepo.save(emp);

        // Actualizar sueldo si viene en el payload
        BigDecimal sueldo = new BigDecimal("3000.00");
        Optional<EmpleadoConfigFinanciera> confOpt = configRepo.findByEmpId(empId);
        EmpleadoConfigFinanciera conf = confOpt.orElse(new EmpleadoConfigFinanciera(empId));
        if (req.getSueldoMensual() != null) {
            conf.setSueldoMensual(req.getSueldoMensual());
        }
        conf.setCargo(emp.getCargo());
        configRepo.save(conf);
        sueldo = conf.getSueldoMensual();

        // Retroalimentar nombres a registros de asistencia si cambiaron
        syncService.enriquecerRegistrosAsistenciaHistoricos();

        EmpleadoDto resultado = convertirADto(guardado);
        resultado.setSueldoMensual(sueldo);
        return ResponseEntity.ok(resultado);
    }

    /**
     * DELETE /api/v1/empleados/{empId}
     * Desactiva a un empleado del padrón (Soft Delete).
     * Restringido a ROLE_ADMIN.
     */
    @DeleteMapping("/{empId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> desactivarEmpleado(@PathVariable String empId) {
        return empleadoRepo.findById(empId).map(e -> {
            e.setActivo(false);
            empleadoRepo.save(e);
            return ResponseEntity.ok(Map.of("mensaje", "Empleado desactivado exitosamente (Soft Delete).", "empId", empId));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/v1/empleados/{empId}/activar
     * Reactiva a un empleado en el padrón activo.
     * Restringido a ROLE_ADMIN.
     */
    @PutMapping("/{empId}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activarEmpleado(@PathVariable String empId) {
        return empleadoRepo.findById(empId).map(e -> {
            e.setActivo(true);
            empleadoRepo.save(e);
            return ResponseEntity.ok(Map.of("mensaje", "Empleado reactivado exitosamente.", "empId", empId));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Departamento resolverDepartamento(Long id, String nombre) {
        if (id != null) {
            return departamentoRepo.findById(id).orElse(null);
        }
        if (nombre != null && !nombre.trim().isEmpty()) {
            return departamentoRepo.findByNombreIgnoreCase(nombre.trim()).orElse(null);
        }
        return null;
    }

    private EmpleadoDto convertirADto(Empleado e) {
        return new EmpleadoDto(
                e.getEmpId(),
                e.getNombres(),
                e.getApellidos(),
                e.getCargo(),
                e.getFechaContratacion(),
                e.getEmpresa(),
                e.getDepartamento() != null ? e.getDepartamento().getId() : null,
                e.getDepartamentoNombre(),
                e.getDepartamento() != null ? e.getDepartamento().getRutaJerarquica() : e.getDepartamentoNombre(),
                e.getActivo(),
                e.isDesconocido()
        );
    }
}
