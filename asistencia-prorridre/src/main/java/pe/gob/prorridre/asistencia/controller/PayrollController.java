package pe.gob.prorridre.asistencia.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.gob.prorridre.asistencia.dto.ConfigFinancieraRequestDto;
import pe.gob.prorridre.asistencia.dto.JustificarRequestDto;
import pe.gob.prorridre.asistencia.dto.LiquidacionPagoDto;
import pe.gob.prorridre.asistencia.model.EmpleadoConfigFinanciera;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import pe.gob.prorridre.asistencia.service.PayrollService;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador REST para el Módulo de Planillas, Pagos, Descuentos y Auditoría del Doctor.
 */
@RestController
@RequestMapping("/api/v1/planillas")
@CrossOrigin(origins = "*")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    /**
     * GET /api/v1/planillas
     * Obtiene la liquidación mensual completa de todos los trabajadores o filtrados.
     */
    @GetMapping
    public ResponseEntity<List<LiquidacionPagoDto>> liquidarPlanilla(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String empId) {

        List<LiquidacionPagoDto> lista = payrollService.liquidarPlanilla(fechaInicio, fechaFin, departamento, empId);
        return ResponseEntity.ok(lista);
    }

    /**
     * GET /api/v1/planillas/{empId}
     * Liquidación mensual detallada de un trabajador individual.
     */
    @GetMapping("/{empId}")
    public ResponseEntity<LiquidacionPagoDto> liquidarIndividual(
            @PathVariable String empId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        LiquidacionPagoDto dto = payrollService.liquidarEmpleadoIndividual(empId, fechaInicio, fechaFin);
        return ResponseEntity.ok(dto);
    }

    /**
     * GET /api/v1/planillas/config
     * Lista todas las configuraciones financieras de haberes y cargos.
     */
    @GetMapping("/config")
    public ResponseEntity<List<EmpleadoConfigFinanciera>> listarConfiguraciones() {
        return ResponseEntity.ok(payrollService.listarConfiguraciones());
    }

    /**
     * GET /api/v1/planillas/config/{empId}
     * Obtiene la configuración financiera específica de un trabajador.
     */
    @GetMapping("/config/{empId}")
    public ResponseEntity<EmpleadoConfigFinanciera> obtenerConfiguracion(@PathVariable String empId) {
        return ResponseEntity.ok(payrollService.obtenerOConfigurarFinanciera(empId));
    }

    /**
     * PUT /api/v1/planillas/config
     * Actualiza el sueldo mensual, días base, multa por omisión o compensación de horas extra.
     */
    @PutMapping("/config")
    public ResponseEntity<EmpleadoConfigFinanciera> guardarConfiguracion(
            @RequestBody ConfigFinancieraRequestDto req) {
        return ResponseEntity.ok(payrollService.actualizarConfigFinanciera(req));
    }

    /**
     * POST /api/v1/planillas/justificar/{id}
     * Alias del endpoint de justificación rápida del Doctor.
     */
    @PostMapping("/justificar/{id}")
    public ResponseEntity<RegistroAsistencia> justificarRegistro(
            @PathVariable Long id,
            @RequestBody JustificarRequestDto req) {
        return ResponseEntity.ok(payrollService.justificarRegistro(id, req));
    }
}

