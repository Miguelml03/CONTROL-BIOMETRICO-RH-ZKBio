package pe.gob.prorridre.asistencia.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.dto.DepartamentoDto;
import pe.gob.prorridre.asistencia.dto.EmpleadoDto;
import pe.gob.prorridre.asistencia.dto.SincronizacionMaestrosResultDto;
import pe.gob.prorridre.asistencia.model.Departamento;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.service.ZKBioTimeSyncService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador REST para la gestión y sincronización de datos maestros
 * (Departamentos/Metas institucionales y Nómina de Empleados).
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class MaestroController {

    private final ZKBioTimeSyncService syncService;
    private final DepartamentoRepository departamentoRepo;
    private final EmpleadoRepository empleadoRepo;

    public MaestroController(ZKBioTimeSyncService syncService,
                             DepartamentoRepository departamentoRepo,
                             EmpleadoRepository empleadoRepo) {
        this.syncService = syncService;
        this.departamentoRepo = departamentoRepo;
        this.empleadoRepo = empleadoRepo;
    }

    /**
     * POST /api/v1/sincronizar-maestros
     * Endpoint directo solicitado para descargar y sincronizar en vivo
     * la estructura jerárquica de Departamentos y el padrón de Empleados desde ZKBio Time 8.0.8.
     */
    @PostMapping("/sincronizar-maestros")
    public ResponseEntity<SincronizacionMaestrosResultDto> sincronizarMaestros() {
        return ResponseEntity.ok(syncService.sincronizarMaestros());
    }

    /**
     * POST /api/v1/maestros/sincronizar
     * Alias dentro del contexto de maestros.
     */
    @PostMapping("/maestros/sincronizar")
    public ResponseEntity<SincronizacionMaestrosResultDto> sincronizarMaestrosAlias() {
        return ResponseEntity.ok(syncService.sincronizarMaestros());
    }

    /**
     * GET /api/v1/maestros/departamentos
     * Devuelve el árbol jerárquico completo de Departamentos y Metas PRORRIDRE.
     * Parámetro opcional ?arbol=true para formato anidado padre-hijos,
     * o false/ausente para lista plana ordenada con ruta jerárquica.
     */
    @GetMapping("/maestros/departamentos")
    public ResponseEntity<List<DepartamentoDto>> listarDepartamentos(
            @RequestParam(required = false, defaultValue = "false") boolean arbol) {

        List<Departamento> todos = departamentoRepo.findAllConPadre();

        // Mapa de conteo de empleados por departamento
        Map<Long, Long> conteoEmpleados = empleadoRepo.findAll().stream()
                .filter(e -> e.getDepartamento() != null && e.getDepartamento().getId() != null)
                .collect(Collectors.groupingBy(e -> e.getDepartamento().getId(), Collectors.counting()));

        if (!arbol) {
            List<DepartamentoDto> listaPlana = todos.stream().map(d -> {
                DepartamentoDto dto = new DepartamentoDto(
                        d.getId(),
                        d.getCodigo(),
                        d.getNombre(),
                        d.getParent() != null ? d.getParent().getId() : null,
                        d.getParent() != null ? d.getParent().getNombre() : null,
                        d.getRutaJerarquica(),
                        d.getNivel()
                );
                dto.setTotalEmpleados(conteoEmpleados.getOrDefault(d.getId(), 0L));
                return dto;
            }).sorted(Comparator.comparing(DepartamentoDto::getRutaJerarquica))
              .collect(Collectors.toList());

            return ResponseEntity.ok(listaPlana);
        }

        // Construcción del árbol jerárquico anidado
        Map<Long, DepartamentoDto> dtoMap = new LinkedHashMap<>();
        for (Departamento d : todos) {
            DepartamentoDto dto = new DepartamentoDto(
                    d.getId(),
                    d.getCodigo(),
                    d.getNombre(),
                    d.getParent() != null ? d.getParent().getId() : null,
                    d.getParent() != null ? d.getParent().getNombre() : null,
                    d.getRutaJerarquica(),
                    d.getNivel()
            );
            dto.setTotalEmpleados(conteoEmpleados.getOrDefault(d.getId(), 0L));
            dtoMap.put(d.getId(), dto);
        }

        List<DepartamentoDto> raices = new ArrayList<>();
        for (DepartamentoDto dto : dtoMap.values()) {
            if (dto.getParentId() == null) {
                raices.add(dto);
            } else {
                DepartamentoDto padre = dtoMap.get(dto.getParentId());
                if (padre != null) {
                    padre.getHijos().add(dto);
                } else {
                    raices.add(dto);
                }
            }
        }

        return ResponseEntity.ok(raices);
    }

    /**
     * GET /api/v1/maestros/empleados
     * Devuelve la nómina de empleados enriquecida con cargo y departamento jerárquico.
     */
    @GetMapping("/maestros/empleados")
    public ResponseEntity<List<EmpleadoDto>> listarEmpleados(
            @RequestParam(required = false) Long departamentoId,
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) Boolean soloDesconocidos) {

        List<Empleado> lista = empleadoRepo.findAll();

        if (Boolean.TRUE.equals(soloDesconocidos)) {
            lista = lista.stream().filter(Empleado::isDesconocido).collect(Collectors.toList());
        }

        if (departamentoId != null) {
            Set<Long> idsDesc = departamentoRepo.obtenerIdsDescendientesDe(departamentoId);
            lista = lista.stream()
                    .filter(e -> e.getDepartamento() != null && idsDesc.contains(e.getDepartamento().getId()))
                    .collect(Collectors.toList());
        }

        if (buscar != null && !buscar.trim().isEmpty()) {
            String b = buscar.trim().toUpperCase();
            lista = lista.stream()
                    .filter(e -> e.getEmpId().toUpperCase().contains(b) ||
                                 e.getNombreCompleto().toUpperCase().contains(b) ||
                                 (e.getCargo() != null && e.getCargo().toUpperCase().contains(b)))
                    .collect(Collectors.toList());
        }

        List<EmpleadoDto> dtos = lista.stream().map(e -> new EmpleadoDto(
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
        )).sorted(Comparator.comparing(EmpleadoDto::getNombreCompleto))
          .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * PUT /api/v1/maestros/empleados/{empId}
     * Permite al Administrador/Doctor actualizar o completar los datos maestros de un empleado
     * (útil cuando fue creado temporalmente como DESCONOCIDO).
     */
    @PutMapping("/maestros/empleados/{empId}")
    public ResponseEntity<EmpleadoDto> actualizarEmpleado(
            @PathVariable String empId,
            @RequestBody EmpleadoDto req) {

        Empleado emp = empleadoRepo.findById(empId)
                .orElseGet(() -> new Empleado(empId, "", "", "PRORRIDRE PUNO", "GENERAL"));

        if (req.getNombres() != null) emp.setNombres(req.getNombres().trim());
        if (req.getApellidos() != null) emp.setApellidos(req.getApellidos().trim());
        if (req.getCargo() != null) emp.setCargo(req.getCargo().trim());
        if (req.getFechaContratacion() != null) emp.setFechaContratacion(req.getFechaContratacion());
        if (req.getActivo() != null) emp.setActivo(req.getActivo());

        if (req.getDepartamentoId() != null) {
            departamentoRepo.findById(req.getDepartamentoId()).ifPresent(emp::setDepartamento);
        } else if (req.getDepartamentoNombre() != null && !req.getDepartamentoNombre().trim().isEmpty()) {
            departamentoRepo.findByNombreIgnoreCase(req.getDepartamentoNombre().trim())
                    .ifPresentOrElse(emp::setDepartamento, () -> emp.setDepartamentoNombre(req.getDepartamentoNombre().trim()));
        }

        Empleado guardado = empleadoRepo.save(emp);

        // Retroalimentar enriquecimiento sobre registros de asistencia
        syncService.enriquecerRegistrosAsistenciaHistoricos();

        return ResponseEntity.ok(new EmpleadoDto(
                guardado.getEmpId(),
                guardado.getNombres(),
                guardado.getApellidos(),
                guardado.getCargo(),
                guardado.getFechaContratacion(),
                guardado.getEmpresa(),
                guardado.getDepartamento() != null ? guardado.getDepartamento().getId() : null,
                guardado.getDepartamentoNombre(),
                guardado.getDepartamento() != null ? guardado.getDepartamento().getRutaJerarquica() : guardado.getDepartamentoNombre(),
                guardado.getActivo(),
                guardado.isDesconocido()
        ));
    }
}
