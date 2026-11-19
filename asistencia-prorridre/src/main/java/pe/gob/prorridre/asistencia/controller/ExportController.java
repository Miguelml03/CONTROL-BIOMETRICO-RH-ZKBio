package pe.gob.prorridre.asistencia.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.gob.prorridre.asistencia.service.ExcelExportService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controlador REST para la exportación de reportes institucionales.
 * Endpoint principal: GET /api/v1/exportar/excel
 */
@RestController
@RequestMapping("/api/v1/exportar")
@CrossOrigin(origins = "*")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);
    private final ExcelExportService exportService;

    public ExportController(ExcelExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * GET /api/v1/exportar/excel
     * Genera y descarga el libro Excel Auditado (.xlsx) con 3 hojas:
     * 1. 20261019 / Resumen de Asistencia (18 columnas A:R con fórmulas y orden numérico).
     * 2. Planilla_Pagos_Descuentos (Resumen financiero por empleado).
     * 3. Glosario y Reglas Técnicas (Documentación de fórmulas y reglas).
     *
     * Permite seleccionar:
     * - mes: "2026-10" o "2026-09" (establece automáticamente el 1er y último día del mes)
     * - fechaInicio y fechaFin: Rango personalizado de días
     * - departamento: Filtro por área / meta
     * - empId: Filtro por código de trabajador
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportarExcelAuditado(
            @RequestParam(required = false) String mes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String empId) {

        // Si se envía el mes (ej: "2026-10" o "2026-09"), calcular automáticamente rango del mes completo
        LocalDate fIni = fechaInicio;
        LocalDate fFin = fechaFin;

        if (mes != null && !mes.trim().isEmpty()) {
            String m = mes.trim();
            if (m.matches("\\d{4}-\\d{2}")) {
                String[] p = m.split("-");
                int anio = Integer.parseInt(p[0]);
                int mesNum = Integer.parseInt(p[1]);
                fIni = LocalDate.of(anio, mesNum, 1);
                fFin = fIni.withDayOfMonth(fIni.lengthOfMonth());
            }
        }

        try {
            byte[] excelBytes = exportService.generarExcelAuditado(fIni, fFin, departamento, empId);

            String sufijo;
            if (mes != null && !mes.trim().isEmpty()) {
                sufijo = mes.replace("-", "");
            } else if (fFin != null) {
                sufijo = fFin.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else {
                sufijo = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            String filename = String.format("PRORRIDRE_Reporte_Biometrico_Auditado_%s.xlsx", sufijo);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);

        } catch (IOException e) {
            log.error("[Exportador] Error generando archivo Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

