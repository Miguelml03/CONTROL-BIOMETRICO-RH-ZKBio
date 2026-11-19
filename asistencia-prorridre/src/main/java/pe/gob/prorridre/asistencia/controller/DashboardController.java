package pe.gob.prorridre.asistencia.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.gob.prorridre.asistencia.dto.DashboardKpiDto;
import pe.gob.prorridre.asistencia.service.DashboardKpiService;

import java.time.LocalDate;

/**
 * Controlador REST para el Dashboard de Asistencia y KPIs PRORRIDRE.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardKpiService dashboardService;

    public DashboardController(DashboardKpiService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/v1/dashboard/resumen
     * Procesa y devuelve todos los KPIs analíticos en tiempo real:
     * - Días-Hombre de ausentismo real (cruce padrón vs días hábiles).
     * - % Puntualidad ("A Tiempo", "Tardanza", "Omisión").
     * - Horas trabajadas, extra y déficit.
     * - Ranking institucional por departamento/meta.
     */
    @GetMapping("/resumen")
    public ResponseEntity<DashboardKpiDto> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String empId) {

        DashboardKpiDto kpis = dashboardService.calcularKpis(fechaInicio, fechaFin, departamento, empId);
        return ResponseEntity.ok(kpis);
    }
}

