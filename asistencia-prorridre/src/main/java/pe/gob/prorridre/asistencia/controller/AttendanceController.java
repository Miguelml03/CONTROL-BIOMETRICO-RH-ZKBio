package pe.gob.prorridre.asistencia.controller;

import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.CalcularVectorRequest;
import pe.gob.prorridre.asistencia.dto.EditarAsistenciaRequestDto;
import pe.gob.prorridre.asistencia.dto.ImportResultDto;
import pe.gob.prorridre.asistencia.dto.JustificarRequestDto;
import pe.gob.prorridre.asistencia.dto.RegistroAsistenciaDto;
import pe.gob.prorridre.asistencia.dto.ResumenIndividualDto;
import pe.gob.prorridre.asistencia.dto.ResumenKpiDto;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import pe.gob.prorridre.asistencia.service.AttendanceEngineService;
import pe.gob.prorridre.asistencia.service.ExcelCsvImportService;
import pe.gob.prorridre.asistencia.service.PayrollService;
import pe.gob.prorridre.asistencia.service.ZkBioIntegrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Controlador REST para el procesamiento y consulta de asistencia biométrica PRORRIDRE PUNO.
 * Expone los registros auditados con las 18 columnas (A:R) y el motor matemático en tiempo real.
 */
@RestController
@RequestMapping("/api/v1/asistencia")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceEngineService engineService;
    private final ExcelCsvImportService importService;
    private final PayrollService payrollService;
    private final ZkBioIntegrationService zkBioIntegrationService;
    private final pe.gob.prorridre.asistencia.service.ZKBioTimeSyncService zkBioTimeSyncService;
    private final RegistroAsistenciaRepository registroRepo;

    public AttendanceController(AttendanceEngineService engineService,
                                ExcelCsvImportService importService,
                                PayrollService payrollService,
                                ZkBioIntegrationService zkBioIntegrationService,
                                pe.gob.prorridre.asistencia.service.ZKBioTimeSyncService zkBioTimeSyncService,
                                RegistroAsistenciaRepository registroRepo) {
        this.engineService = engineService;
        this.importService = importService;
        this.payrollService = payrollService;
        this.zkBioIntegrationService = zkBioIntegrationService;
        this.zkBioTimeSyncService = zkBioTimeSyncService;
        this.registroRepo = registroRepo;
    }

    /**
     * GET /api/v1/asistencia
     * Obtiene la lista de registros auditados con las 18 columnas (A:R).
     * Soporta filtros opcionales por fecha, código de empleado o departamento/meta.
     */
    @GetMapping
    public ResponseEntity<List<RegistroAsistenciaDto>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String empId,
            @RequestParam(required = false) String departamento) {

        List<RegistroAsistenciaDto> resultado = importService.obtenerRegistros(fecha, empId, departamento);

        // Si la tabla de registros está vacía, sincronizar automáticamente desde marcacion
        if (resultado.isEmpty() && (fecha == null && empId == null && departamento == null)) {
            importService.sincronizarDesdeMarcaciones();
            resultado = importService.obtenerRegistros(null, null, null);
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * GET /api/v1/asistencia/resumen
     * Métricas y KPIs de puntualidad y horas efectivas para una fecha específica o la más reciente.
     */
    @GetMapping("/resumen")
    public ResponseEntity<ResumenKpiDto> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(importService.calcularResumen(fecha));
    }

    /**
     * GET /api/v1/asistencia/resumen-individual
     * Tabla de RESUMEN INDIVIDUAL con métricas de los 4 pilares y tardanzas
     * para el trabajador y período filtrado.
     */
    @GetMapping("/resumen-individual")
    public ResponseEntity<ResumenIndividualDto> resumenIndividual(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String empId,
            @RequestParam(required = false) String departamento) {

        List<RegistroAsistencia> registros;
        if (fechaInicio != null && fechaFin != null) {
            if (empId != null && !empId.trim().isEmpty()) {
                registros = registroRepo.findByEmpIdAndFechaBetweenOrderByFechaAsc(empId.trim(), fechaInicio, fechaFin);
            } else if (departamento != null && !departamento.trim().isEmpty()) {
                registros = registroRepo.findByFechaBetweenAndDepartamentoIgnoreCaseOrderByFechaAscApellidosAsc(fechaInicio, fechaFin, departamento.trim());
            } else {
                registros = registroRepo.findByFechaBetweenOrderByFechaDescDepartamentoAscApellidosAsc(fechaInicio, fechaFin);
            }
        } else if (empId != null && !empId.trim().isEmpty()) {
            registros = registroRepo.findByEmpIdOrderByFechaDesc(empId.trim());
        } else if (departamento != null && !departamento.trim().isEmpty()) {
            registros = registroRepo.findByDepartamentoIgnoreCaseOrderByFechaDescApellidosAsc(departamento.trim());
        } else {
            registros = registroRepo.findAllByOrderByFechaDescApellidosAsc();
        }

        return ResponseEntity.ok(engineService.calcularResumenIndividual(registros));
    }

    /**
     * GET /api/v1/asistencia/departamentos
     * Lista de los departamentos / metas institucionales con registros activos.
     */
    @GetMapping("/departamentos")
    public ResponseEntity<List<String>> departamentos() {
        return ResponseEntity.ok(importService.obtenerDepartamentos());
    }

    /**
     * GET /api/v1/asistencia/fechas
     * Lista de fechas auditadas disponibles en el sistema.
     */
    @GetMapping("/fechas")
    public ResponseEntity<List<LocalDate>> fechas() {
        return ResponseEntity.ok(importService.obtenerFechasDisponibles());
    }

    /**
     * POST /api/v1/asistencia/sincronizar
     * Ejecuta la sincronización en vivo con ZKTeco / ZKBio Time 8.0.8 trayendo marcaciones
     * incrementales a H2 y recalculando el motor matemático auditado sobre las 18 columnas.
     */
    @PostMapping("/sincronizar")
    public ResponseEntity<ImportResultDto> sincronizarDesdeBd() {
        return ResponseEntity.ok(zkBioIntegrationService.sincronizarMarcacionesEnVivo());
    }

    /**
     * POST /api/v1/asistencia/sincronizar-maestros
     * Descarga y sincroniza la estructura de Departamentos y Nómina de Empleados
     * enriqueciendo automáticamente las columnas de nombres y departamentos.
     */
    @PostMapping("/sincronizar-maestros")
    public ResponseEntity<pe.gob.prorridre.asistencia.dto.SincronizacionMaestrosResultDto> sincronizarMaestros() {
        return ResponseEntity.ok(zkBioTimeSyncService.sincronizarMaestros());
    }

    /**
     * POST /api/v1/asistencia/importar
     * Permite subir un archivo Excel (.XLSX) o CSV para procesarlo con el motor matemático auditado.
     */
    @PostMapping("/importar")
    public ResponseEntity<ImportResultDto> importarArchivo(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(importService.importarArchivo(archivo));
    }

    /**
     * POST /api/v1/asistencia/calcular-vector
     * Endpoint utilitario y de auditoría para probar en vivo el motor con un vector de horas crudas.
     */
    @PostMapping("/calcular-vector")
    public ResponseEntity<RegistroAsistencia> probarVector(@RequestBody CalcularVectorRequest req) {
        RegistroAsistencia reg = engineService.calcularRegistro(
                req.getEmpId(),
                req.getNombres(),
                req.getApellidos(),
                "PRORRIDRE",
                req.getDepartamento(),
                LocalDate.now(),
                req.getHorasCrudas()
        );
        return ResponseEntity.ok(reg);
    }

    /**
     * POST /api/v1/asistencia/{id}/justificar
     * Endpoint de Acción Rápida del Doctor:
     * Permite cambiar el estado de una fila a JUSTIFICADO_MEDICO, COMISION_SERVICIO,
     * PAPELETA_PERMISO, REGULARIZADO_POR_DIRECTOR o APLICAR_DESCUENTO, registrando
     * la observación y recalculando de inmediato tardanza y balance.
     */
    @PostMapping("/{id}/justificar")
    public ResponseEntity<RegistroAsistencia> justificarRegistro(
            @PathVariable Long id,
            @RequestBody JustificarRequestDto req) {
        return ResponseEntity.ok(payrollService.justificarRegistro(id, req));
    }

    /**
     * PUT /api/v1/asistencia/{id}
     * Edición manual de asistencia por parte del Administrador (Director / Doctor).
     * Modifica las horas timbradas y recalcula automáticamente minutos de tardanza y balance.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> editarRegistro(
            @PathVariable Long id,
            @RequestBody EditarAsistenciaRequestDto req) {
        return registroRepo.findById(id).map(reg -> {
            if (req.getHoraEntrada() != null) reg.setHoraEntrada(req.getHoraEntrada());
            if (req.getInicioAlmuerzo() != null) reg.setInicioAlmuerzo(req.getInicioAlmuerzo());
            if (req.getFinAlmuerzo() != null) reg.setFinAlmuerzo(req.getFinAlmuerzo());
            if (req.getHoraSalida() != null) reg.setHoraSalida(req.getHoraSalida());
            if (req.getHorasCrudas() != null) reg.setHorasCrudas(req.getHorasCrudas().trim());
            if (req.getObservacionDoctor() != null) reg.setObservacionDoctor(req.getObservacionDoctor().trim());

            if (Boolean.TRUE.equals(req.getRecalcularAutomatico())) {
                engineService.reevaluarTardanzaYCumplimiento(reg);
            } else {
                if (req.getCondicionIngreso() != null) reg.setCondicionIngreso(req.getCondicionIngreso());
                if (req.getMinutosTardanza() != null) reg.setMinutosTardanza(req.getMinutosTardanza());
            }

            reg.setActualizadoEn(LocalDateTime.now());
            RegistroAsistencia guardado = registroRepo.save(reg);
            return ResponseEntity.ok(guardado);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/v1/asistencia/{id}
     * Eliminación de un registro erróneo o duplicado de asistencia por parte del Administrador.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> eliminarRegistro(@PathVariable Long id) {
        if (!registroRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        registroRepo.deleteById(id);
        return ResponseEntity.ok(Collections.singletonMap("mensaje", "Registro de asistencia #" + id + " eliminado exitosamente."));
    }
}

