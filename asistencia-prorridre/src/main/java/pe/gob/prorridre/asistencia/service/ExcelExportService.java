package pe.gob.prorridre.asistencia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.LiquidacionPagoDto;
import pe.gob.prorridre.asistencia.dto.ResumenIndividualDto;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servicio de Generación y Exportación de Excel Auditado (.xlsx).
 * Genera un libro OpenXML (.xlsx) estándar con 3 hojas:
 * 1. Resumen_Asistencia_Depurado (18 columnas A:R con fórmulas corregidas y 0
 * errores #¡VALOR!).
 * 2. Planilla_Pagos_Descuentos (Resumen financiero por trabajador con sueldos y
 * descuentos).
 * 3. Glosario y Reglas Tecnicas (Columna C de fórmulas Excel 100% completada).
 *
 * Implementado con ZipOutputStream y OpenXML nativo (sin dependencias pesadas
 * externas).
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    private final RegistroAsistenciaRepository registroRepo;
    private final PayrollService payrollService;
    private final AttendanceEngineService engineService;

    public ExcelExportService(RegistroAsistenciaRepository registroRepo,
            PayrollService payrollService,
            AttendanceEngineService engineService) {
        this.registroRepo = registroRepo;
        this.payrollService = payrollService;
        this.engineService = engineService;
    }

    /**
     * Genera el libro Excel (.xlsx) completo en memoria como byte array.
     */
    @Transactional(readOnly = true)
    public byte[] generarExcelAuditado(LocalDate fechaInicio,
            LocalDate fechaFin,
            String departamento,
            String empId) throws IOException {

        log.info("[Exportador] Generando Excel auditado para rango [{} - {}], depto={}, empId={}",
                fechaInicio, fechaFin, departamento, empId);

        // 1. Obtener datos de asistencia
        List<RegistroAsistencia> registros;
        if (fechaInicio != null && fechaFin != null) {
            if (empId != null && !empId.trim().isEmpty()) {
                registros = registroRepo.findByEmpIdAndFechaBetweenOrderByFechaAsc(empId.trim(), fechaInicio, fechaFin);
            } else if (departamento != null && !departamento.trim().isEmpty()) {
                registros = registroRepo.findByFechaBetweenAndDepartamentoIgnoreCaseOrderByFechaAscApellidosAsc(
                        fechaInicio, fechaFin, departamento.trim());
            } else {
                registros = registroRepo.findByFechaBetweenOrderByFechaDescDepartamentoAscApellidosAsc(fechaInicio,
                        fechaFin);
            }
        } else if (departamento != null && !departamento.trim().isEmpty()) {
            registros = registroRepo.findByDepartamentoIgnoreCaseOrderByFechaDescApellidosAsc(departamento.trim());
        } else if (empId != null && !empId.trim().isEmpty()) {
            registros = registroRepo.findByEmpIdOrderByFechaDesc(empId.trim());
        } else {
            registros = registroRepo.findAllByOrderByFechaDescApellidosAsc();
        }

        // Orden estricto según la captura oficial:
        // 1° ID numérico ascendente (1, 4, 5, 7, 13, 16, 93, 272...)
        // 2° Fecha cronológica ascendente (22-09-2026 -> 19-10-2026)
        registros.sort((r1, r2) -> {
            long id1 = parseIdNumerico(r1.getEmpId());
            long id2 = parseIdNumerico(r2.getEmpId());
            if (id1 != id2) {
                return Long.compare(id1, id2);
            }
            if (r1.getFecha() != null && r2.getFecha() != null) {
                return r1.getFecha().compareTo(r2.getFecha());
            }
            return 0;
        });

        // Nombre de la pestaña principal acorde al periodo (ej. 20261019 como en la
        // captura)
        String nombrePestana1 = "Resumen Asistencia";
        if (fechaFin != null) {
            nombrePestana1 = fechaFin.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        // 2. Obtener datos de liquidación de planilla
        List<LiquidacionPagoDto> planillas = payrollService.liquidarPlanilla(fechaInicio, fechaFin, departamento,
                empId);

        // 3. Empaquetar el archivo ZIP / XLSX
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            // [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml());

            // _rels/.rels
            addZipEntry(zos, "_rels/.rels", buildRootRelsXml());

            // xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml());

            // xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(nombrePestana1));

            // xl/styles.xml
            addZipEntry(zos, "xl/styles.xml", buildStylesXml());

            // xl/worksheets/sheet1.xml (Resumen Asistencia)
            addZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheet1AsistenciaXml(registros));

            // xl/worksheets/sheet2.xml (Planilla Pagos)
            addZipEntry(zos, "xl/worksheets/sheet2.xml", buildSheet2PlanillaXml(planillas));

            // xl/worksheets/sheet3.xml (Glosario y Reglas)
            addZipEntry(zos, "xl/worksheets/sheet3.xml", buildSheet3GlosarioXml());

            zos.finish();
        }

        log.info("[Exportador] Libro Excel auditado generado exitosamente ({} bytes).", baos.size());
        return baos.toByteArray();
    }

    // ── Estructura OpenXML ──────────────────────────────────────────────────

    private static void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }

    private String buildContentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
                +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n"
                +
                "  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n"
                +
                "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                +
                "  <Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                +
                "  <Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                +
                "</Types>";
    }

    private String buildRootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n"
                +
                "</Relationships>";
    }

    private String buildWorkbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n"
                +
                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>\n"
                +
                "  <Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>\n"
                +
                "  <Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n"
                +
                "</Relationships>";
    }

    private static long parseIdNumerico(String empId) {
        if (empId == null)
            return Long.MAX_VALUE;
        String clean = empId.replaceAll("\\D+", "");
        try {
            return clean.isEmpty() ? Long.MAX_VALUE : Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private String buildWorkbookXml(String pestana1) {
        String safePestana = (pestana1 != null && !pestana1.trim().isEmpty())
                ? escapeXml(pestana1.trim())
                : "Resumen_Asistencia";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n"
                +
                "  <bookViews><workbookView xWindow=\"0\" yWindow=\"0\" windowWidth=\"22000\" windowHeight=\"12000\"/></bookViews>\n"
                +
                "  <sheets>\n" +
                "    <sheet name=\"" + safePestana + "\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                "    <sheet name=\"Planilla_Pagos_Descuentos\" sheetId=\"2\" r:id=\"rId2\"/>\n" +
                "    <sheet name=\"Glosario y Reglas Tecnicas\" sheetId=\"3\" r:id=\"rId3\"/>\n" +
                "  </sheets>\n" +
                "</workbook>";
    }

    private String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <numFmts count=\"2\">\n" +
                "    <numFmt numFmtId=\"165\" formatCode=\"hh:mm:ss\"/>\n" +
                "    <numFmt numFmtId=\"166\" formatCode=\"dd-mm-yyyy\"/>\n" +
                "  </numFmts>\n" +
                "  <fonts count=\"8\">\n" +
                "    <font><sz val=\"10\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"10\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"11\"/><color rgb=\"FF0D2B4E\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"10\"/><color rgb=\"FF1A7F37\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"12\"/><color rgb=\"FF000000\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"10\"/><color rgb=\"FFB91C1C\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"10\"/><color rgb=\"FF922B21\"/><name val=\"Calibri\"/></font>\n" +
                "    <font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>\n" +
                "  </fonts>\n" +
                "  <fills count=\"9\">\n" +
                "    <fill><patternFill patternType=\"none\"/></fill>\n" +
                "    <fill><patternFill patternType=\"gray125\"/></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0D2B4E\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD6E8F7\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF1F5F9\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCFCE7\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFEE2E2\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E78\"/></patternFill></fill>\n" +
                "    <fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFADBD8\"/></patternFill></fill>\n" +
                "  </fills>\n" +
                "  <borders count=\"3\">\n" +
                "    <border><left/><right/><top/><bottom/><diagonal/></border>\n" +
                "    <border>\n" +
                "      <left style=\"thin\"><color rgb=\"FFD5E1EE\"/></left>\n" +
                "      <right style=\"thin\"><color rgb=\"FFD5E1EE\"/></right>\n" +
                "      <top style=\"thin\"><color rgb=\"FFD5E1EE\"/></top>\n" +
                "      <bottom style=\"thin\"><color rgb=\"FFD5E1EE\"/></bottom>\n" +
                "    </border>\n" +
                "    <border>\n" +
                "      <left style=\"thin\"><color rgb=\"FF000000\"/></left>\n" +
                "      <right style=\"thin\"><color rgb=\"FF000000\"/></right>\n" +
                "      <top style=\"thin\"><color rgb=\"FF000000\"/></top>\n" +
                "      <bottom style=\"thin\"><color rgb=\"FF000000\"/></bottom>\n" +
                "    </border>\n" +
                "  </borders>\n" +
                "  <cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>\n" +
                "  <cellXfs count=\"18\">\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\"/>\n" +
                "    <xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\"/>\n" +
                "    <xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>\n" +
                "    <xf numFmtId=\"2\" fontId=\"3\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyNumberFormat=\"1\" applyBorder=\"1\"/>\n" +
                "    <xf numFmtId=\"0\" fontId=\"4\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"3\" fillId=\"5\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"5\" fillId=\"6\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"7\" fillId=\"7\" borderId=\"2\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"2\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"2\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"2\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"6\" fillId=\"8\" borderId=\"2\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"2\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"2\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "    <xf numFmtId=\"0\" fontId=\"6\" fillId=\"8\" borderId=\"2\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>\n" +
                "  </cellXfs>\n" +
                "</styleSheet>";
    }

    // ── Hoja 1: Resumen Asistencia Depurado (18 Columnas A:R + Justificación) ─

    private String buildSheet1AsistenciaXml(List<RegistroAsistencia> registros) {
        StringBuilder sb = new StringBuilder(256 * 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");

        // Anchos de columna óptimos para evitar truncamiento y celdas '######'
        sb.append("  <cols>\n");
        sb.append("    <col min=\"1\" max=\"1\" width=\"10\" customWidth=\"1\"/>\n");  // A: Id
        sb.append("    <col min=\"2\" max=\"2\" width=\"20\" customWidth=\"1\"/>\n");  // B: Nombres
        sb.append("    <col min=\"3\" max=\"3\" width=\"24\" customWidth=\"1\"/>\n");  // C: Apellidos
        sb.append("    <col min=\"4\" max=\"4\" width=\"20\" customWidth=\"1\"/>\n");  // D: Empresa
        sb.append("    <col min=\"5\" max=\"5\" width=\"24\" customWidth=\"1\"/>\n");  // E: Departamento
        sb.append("    <col min=\"6\" max=\"6\" width=\"13\" customWidth=\"1\"/>\n");  // F: Fecha
        sb.append("    <col min=\"7\" max=\"7\" width=\"8\" customWidth=\"1\"/>\n");   // G: Núm
        sb.append("    <col min=\"8\" max=\"8\" width=\"42\" customWidth=\"1\"/>\n");  // H: Hora
        sb.append("    <col min=\"9\" max=\"9\" width=\"14\" customWidth=\"1\"/>\n");  // I: Hora Entrada
        sb.append("    <col min=\"10\" max=\"10\" width=\"16\" customWidth=\"1\"/>\n"); // J: Salida Almuerzo
        sb.append("    <col min=\"11\" max=\"11\" width=\"16\" customWidth=\"1\"/>\n"); // K: Entrada Almuerzo
        sb.append("    <col min=\"12\" max=\"12\" width=\"14\" customWidth=\"1\"/>\n"); // L: Hora Salida
        sb.append("    <col min=\"13\" max=\"13\" width=\"18\" customWidth=\"1\"/>\n"); // M: Condición Ingreso
        sb.append("    <col min=\"14\" max=\"14\" width=\"16\" customWidth=\"1\"/>\n"); // N: Minutos Tardanza
        sb.append("    <col min=\"15\" max=\"15\" width=\"16\" customWidth=\"1\"/>\n"); // O: Jornada Efectiva
        sb.append("    <col min=\"16\" max=\"16\" width=\"22\" customWidth=\"1\"/>\n"); // P: Jornada Reglamentaria
        sb.append("    <col min=\"17\" max=\"17\" width=\"16\" customWidth=\"1\"/>\n"); // Q: Balance Jornada
        sb.append("    <col min=\"18\" max=\"18\" width=\"26\" customWidth=\"1\"/>\n"); // R: Estado Cumplimiento
        sb.append("    <col min=\"19\" max=\"19\" width=\"22\" customWidth=\"1\"/>\n"); // S: Justificación Doctor
        sb.append("    <col min=\"20\" max=\"20\" width=\"50\" customWidth=\"1\"/>\n"); // T: Observación / Sustento
        sb.append("  </cols>\n");

        sb.append("  <sheetData>\n");

        // Fila 1: Título "Resumen de Asistencia" centrado (igual a la captura oficial)
        sb.append("    <row r=\"1\" ht=\"25\" customHeight=\"1\">\n");
        sb.append("      <c r=\"A1\" s=\"5\" t=\"inlineStr\"><is><t>Resumen de Asistencia</t></is></c>\n");
        for (int i = 2; i <= 20; i++) {
            sb.append("      <c r=\"").append(getColLetter(i)).append("1\" s=\"5\"/>\n");
        }
        sb.append("    </row>\n");

        // Fila 2: Encabezados A:T idénticos a la captura oficial
        String[] headers = {
                "Id", "Nombres", "Apellidos", "nombre de empresa",
                "Departamento", "Fecha", "Núm",
                "Hora", "Hora Entrada", "Salida Almuerzo",
                "Entrada Almuerzo", "Hora Salida", "Condición Ingreso",
                "Minutos Tardanza", "Jornada Efectiva", "Jornada Reglamentaria",
                "Balance Jornada", "Estado Cumplimiento",
                "Justificación Doctor", "Observación / Sustento"
        };

        sb.append("    <row r=\"2\">\n");
        for (int i = 0; i < headers.length; i++) {
            String col = getColLetter(i + 1);
            sb.append("      <c r=\"").append(col).append("2\" s=\"1\" t=\"inlineStr\"><is><t>")
                    .append(escapeXml(headers[i])).append("</t></is></c>\n");
        }
        sb.append("    </row>\n");

        // Filas de datos a partir de la fila 3
        DateTimeFormatter fmtFechaExcel = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        int rowIdx = 3;
        for (RegistroAsistencia r : registros) {
            sb.append("    <row r=\"").append(rowIdx).append("\">\n");

            // A: Id
            appendCellStr(sb, "A", rowIdx, 0, r.getEmpId() != null ? r.getEmpId() : "");
            // B: Nombres
            appendCellStr(sb, "B", rowIdx, 0, r.getNombres() != null ? r.getNombres() : "");
            // C: Apellidos
            appendCellStr(sb, "C", rowIdx, 0, r.getApellidos() != null ? r.getApellidos() : "");
            // D: nombre de empresa
            appendCellStr(sb, "D", rowIdx, 0, r.getEmpresa() != null ? r.getEmpresa() : "PRORRIDRE PUNO");
            // E: Departamento
            appendCellStr(sb, "E", rowIdx, 0, r.getDepartamento() != null ? r.getDepartamento() : "");
            // F: Fecha (Formato dd-MM-yyyy como en la captura)
            String fechaFmt = r.getFecha() != null ? r.getFecha().format(fmtFechaExcel) : "";
            appendCellStr(sb, "F", rowIdx, 0, fechaFmt);
            // G: Núm
            appendCellNum(sb, "G", rowIdx, 0, r.getNumMarcaciones() != null ? r.getNumMarcaciones() : 0);
            // H: Hora (marcaciones separadas por comas)
            appendCellStr(sb, "H", rowIdx, 0, r.getHorasCrudas() != null ? r.getHorasCrudas() : "");

            boolean esSabado = r.getFecha() != null && r.getFecha().getDayOfWeek() == DayOfWeek.SATURDAY;

            if (esSabado) {
                // SÁBADOS: Voluntario / No computable en horas semanales
                // I: Hora Entrada
                appendCellTime(sb, "I", rowIdx, 6, r.getHoraEntrada());
                // J y K: Vacías (sin refrigerio reglamentario)
                appendCellStr(sb, "J", rowIdx, 0, "");
                appendCellStr(sb, "K", rowIdx, 0, "");
                // L: Hora Salida informativa si existe segunda marcación
                appendCellTime(sb, "L", rowIdx, 6, r.getHoraSalida());

                // M: Condición Ingreso
                String condVal;
                int condStyle;
                if (r.getHoraEntrada() == null) {
                    condVal = "Sin Marcación";
                    condStyle = 0;
                } else if (!r.getHoraEntrada().isAfter(java.time.LocalTime.of(8, 10, 0))) {
                    condVal = "A Tiempo";
                    condStyle = 8;
                } else {
                    condVal = "Tardanza";
                    condStyle = 9;
                }
                String condFormula = String.format(
                        "IF(OR(ISBLANK(I%d),I%d=\"\"),\"Sin Marcación\",IF(I%d<=TIME(8,10,0),\"A Tiempo\",\"Tardanza\"))",
                        rowIdx, rowIdx, rowIdx);
                appendCellFormulaStr(sb, "M", rowIdx, condStyle, condFormula, condVal);

                // N: Minutos Tardanza
                String tardFormula = String.format(
                        "IF(OR(ISBLANK(I%d),I%d=\"\",I%d<=TIME(8,10,0)),0,MAX(0,ROUND((I%d-TIME(8,10,0))*1440,0)))",
                        rowIdx, rowIdx, rowIdx, rowIdx);
                long tardVal = 0L;
                if (r.getHoraEntrada() != null && r.getHoraEntrada().isAfter(java.time.LocalTime.of(8, 10, 0))) {
                    long segs = java.time.temporal.ChronoUnit.SECONDS.between(java.time.LocalTime.of(8, 10, 0), r.getHoraEntrada());
                    tardVal = Math.max(0L, Math.round(segs / 60.0));
                }
                appendCellFormulaNum(sb, "N", rowIdx, 0, tardFormula, tardVal);

                // O: Jornada Efectiva -> No computable (vacío)
                appendCellStr(sb, "O", rowIdx, 0, "");
                // P: Jornada Reglamentaria -> "--" (NO "08:00:00")
                appendCellStr(sb, "P", rowIdx, 0, "--");
                // Q: Balance Jornada -> vacío
                appendCellStr(sb, "Q", rowIdx, 0, "");
                // R: Estado Cumplimiento
                String estadoSabado = r.getHoraEntrada() != null ? "Jornada Sabatina" : "Sin Ingreso (Sábado)";
                appendCellStr(sb, "R", rowIdx, 0, estadoSabado);
                // S: Justificación Doctor
                appendCellStr(sb, "S", rowIdx, 0, r.getEstadoJustificacion() != null ? r.getEstadoJustificacion() : "NINGUNO");
                // T: Observación / Sustento
                String obsSabado = r.getHoraEntrada() != null ? "JORNADA SABATINA - SIN INCIDENCIAS" : "NO REGISTRA INGRESO";
                appendCellStr(sb, "T", rowIdx, 0, obsSabado);

            } else {
                // LUNES A VIERNES: 4 pilares estrictos
                // I: Hora Entrada
                appendCellTime(sb, "I", rowIdx, 6, r.getHoraEntrada());
                // J: Salida Almuerzo
                appendCellTime(sb, "J", rowIdx, 6, r.getInicioAlmuerzo());
                // K: Entrada Almuerzo
                appendCellTime(sb, "K", rowIdx, 6, r.getFinAlmuerzo());
                // L: Hora Salida
                appendCellTime(sb, "L", rowIdx, 6, r.getHoraSalida());

                // M: Condición Ingreso
                String condVal;
                int condStyle;
                if (r.getHoraEntrada() == null) {
                    condVal = "Sin Marcación";
                    condStyle = 0;
                } else if (!r.getHoraEntrada().isAfter(java.time.LocalTime.of(8, 10, 0))) {
                    condVal = "A Tiempo";
                    condStyle = 8;
                } else {
                    condVal = "Tardanza";
                    condStyle = 9;
                }
                String condFormula = String.format(
                        "IF(OR(ISBLANK(I%d),I%d=\"\"),\"Sin Marcación\",IF(I%d<=TIME(8,10,0),\"A Tiempo\",\"Tardanza\"))",
                        rowIdx, rowIdx, rowIdx);
                appendCellFormulaStr(sb, "M", rowIdx, condStyle, condFormula, condVal);

                // N: Minutos Tardanza
                String tardFormula = String.format(
                        "IF(OR(ISBLANK(I%d),I%d=\"\",I%d<=TIME(8,10,0)),0,MAX(0,ROUND((I%d-TIME(8,10,0))*1440,0)))",
                        rowIdx, rowIdx, rowIdx, rowIdx);
                long tardVal = 0L;
                if (r.getHoraEntrada() != null && r.getHoraEntrada().isAfter(java.time.LocalTime.of(8, 10, 0))) {
                    long segs = java.time.temporal.ChronoUnit.SECONDS.between(java.time.LocalTime.of(8, 10, 0), r.getHoraEntrada());
                    tardVal = Math.max(0L, Math.round(segs / 60.0));
                }
                appendCellFormulaNum(sb, "N", rowIdx, 0, tardFormula, tardVal);

                boolean cuatroCompletos = r.getHoraEntrada() != null
                        && r.getInicioAlmuerzo() != null
                        && r.getFinAlmuerzo() != null
                        && r.getHoraSalida() != null;

                // O: Jornada Efectiva (Solo si los 4 pilares están completos)
                String efecFormula = String.format(
                        "IF(OR(ISBLANK(I%d),I%d=\"\",ISBLANK(J%d),J%d=\"\",ISBLANK(K%d),K%d=\"\",ISBLANK(L%d),L%d=\"\"),\"\",(J%d-I%d)+(L%d-K%d))",
                        rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx,
                        rowIdx, rowIdx, rowIdx, rowIdx);
                Double efecFraction = null;
                if (cuatroCompletos && r.getHorasEfectivasDecimal() != null && r.getHorasEfectivasDecimal() > 0) {
                    efecFraction = r.getHorasEfectivasDecimal() / 24.0;
                }
                if (efecFraction != null) {
                    appendCellFormulaTime(sb, "O", rowIdx, 6, efecFormula, efecFraction);
                } else {
                    appendCellFormulaStr(sb, "O", rowIdx, 0, efecFormula, "");
                }

                // P: Jornada Reglamentaria (08:00:00)
                appendCellTime(sb, "P", rowIdx, 6, java.time.LocalTime.of(8, 0, 0));

                // Q: Balance Jornada (Horas decimales)
                String balFormula = String.format("IF(OR(ISBLANK(O%d),O%d=\"\"),\"\",ROUND((O%d-TIME(8,0,0))*24,2))", rowIdx, rowIdx, rowIdx);
                Double balVal = (cuatroCompletos && r.getBalanceJornada() != null) ? r.getBalanceJornada() : null;
                if (balVal != null) {
                    appendCellFormulaNum(sb, "Q", rowIdx, 2, balFormula, balVal);
                } else {
                    appendCellFormulaStr(sb, "Q", rowIdx, 0, balFormula, "");
                }

                // R: Estado Cumplimiento
                String cumFormula = String.format(
                        "IF(OR(ISBLANK(I%d),I%d=\"\",ISBLANK(J%d),J%d=\"\",ISBLANK(K%d),K%d=\"\",ISBLANK(L%d),L%d=\"\"),\"Incompleto (Falta Marcación)\",IF(Q%d>0.08,\"Excedente de Jornada\",IF(Q%d<-0.08,\"Déficit de Jornada\",\"Jornada Reglamentaria\")))",
                        rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx, rowIdx);
                String cumVal = r.getEstadoCumplimiento() != null ? r.getEstadoCumplimiento()
                        : "Incompleto (Falta Marcación)";
                appendCellFormulaStr(sb, "R", rowIdx, 0, cumFormula, cumVal);

                // S: Justificación Doctor
                appendCellStr(sb, "S", rowIdx, 0,
                        r.getEstadoJustificacion() != null ? r.getEstadoJustificacion() : "NINGUNO");

                // T: Observación / Sustento
                appendCellStr(sb, "T", rowIdx, 0, r.getObservacionDoctor() != null ? r.getObservacionDoctor() : "SIN INCIDENCIAS");
            }

            sb.append("    </row>\n");
            rowIdx++;
        }

        // ── TABLA DE RESUMEN INDIVIDUAL AL PIE DE HOJA 1 ─────────────────────────
        ResumenIndividualDto res = engineService.calcularResumenIndividual(registros);
        rowIdx++; // Fila en blanco de separación
        int tRow = rowIdx;

        // Fila 1: Encabezado RESUMEN INDIVIDUAL (#1F4E78, letras blancas en negrita)
        sb.append("    <row r=\"").append(tRow).append("\" ht=\"24\" customHeight=\"1\">\n");
        sb.append("      <c r=\"A").append(tRow).append("\" s=\"10\" t=\"inlineStr\"><is><t>RESUMEN INDIVIDUAL</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow).append("\" s=\"10\"/>\n");
        sb.append("      <c r=\"C").append(tRow).append("\" s=\"10\"/>\n");
        sb.append("    </row>\n");

        // Fila 2: Días con registro en el período
        sb.append("    <row r=\"").append(tRow + 1).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 1).append("\" s=\"11\" t=\"inlineStr\"><is><t>Días con registro en el período:</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 1).append("\" s=\"11\"/>\n");
        sb.append("      <c r=\"C").append(tRow + 1).append("\" s=\"12\"><v>").append(res.getDiasConRegistro()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 3: Tardanzas de ingreso
        sb.append("    <row r=\"").append(tRow + 2).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 2).append("\" s=\"11\" t=\"inlineStr\"><is><t>Tardanzas de ingreso:</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 2).append("\" s=\"11\"/>\n");
        sb.append("      <c r=\"C").append(tRow + 2).append("\" s=\"12\"><v>").append(res.getTardanzasIngresoDias()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 4: Total minutos de tardanza
        sb.append("    <row r=\"").append(tRow + 3).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 3).append("\" s=\"11\" t=\"inlineStr\"><is><t>Total minutos de tardanza:</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 3).append("\" s=\"11\"/>\n");
        sb.append("      <c r=\"C").append(tRow + 3).append("\" s=\"12\"><v>").append(res.getTotalMinutosTardanza()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 5: Sub-encabezado de 3 columnas para matriz de 4 pilares
        sb.append("    <row r=\"").append(tRow + 4).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 4).append("\" s=\"11\" t=\"inlineStr\"><is><t></t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 4).append("\" s=\"13\" t=\"inlineStr\"><is><t>TOTAL DE REGISTROS</t></is></c>\n");
        sb.append("      <c r=\"C").append(tRow + 4).append("\" s=\"14\" t=\"inlineStr\"><is><t>NO REGISTRA</t></is></c>\n");
        sb.append("    </row>\n");

        // Fila 6: 1. Ingreso
        sb.append("    <row r=\"").append(tRow + 5).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 5).append("\" s=\"15\" t=\"inlineStr\"><is><t>1. Ingreso</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 5).append("\" s=\"16\"><v>").append(res.getTotalEsperadoIngreso()).append("</v></c>\n");
        sb.append("      <c r=\"C").append(tRow + 5).append("\" s=\"17\"><v>").append(res.getNoRegistraIngreso()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 7: 2. Salida a refrigerio
        sb.append("    <row r=\"").append(tRow + 6).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 6).append("\" s=\"15\" t=\"inlineStr\"><is><t>2. Salida a refrigerio</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 6).append("\" s=\"16\"><v>").append(res.getTotalEsperadoSalidaRefrigerio()).append("</v></c>\n");
        sb.append("      <c r=\"C").append(tRow + 6).append("\" s=\"17\"><v>").append(res.getNoRegistraSalidaRefrigerio()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 8: 3. Retorno de refrigerio
        sb.append("    <row r=\"").append(tRow + 7).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 7).append("\" s=\"15\" t=\"inlineStr\"><is><t>3. Retorno de refrigerio</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 7).append("\" s=\"16\"><v>").append(res.getTotalEsperadoRetornoRefrigerio()).append("</v></c>\n");
        sb.append("      <c r=\"C").append(tRow + 7).append("\" s=\"17\"><v>").append(res.getNoRegistraRetornoRefrigerio()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 9: 4. Salida final
        sb.append("    <row r=\"").append(tRow + 8).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 8).append("\" s=\"15\" t=\"inlineStr\"><is><t>4. Salida final</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 8).append("\" s=\"16\"><v>").append(res.getTotalEsperadoSalidaFinal()).append("</v></c>\n");
        sb.append("      <c r=\"C").append(tRow + 8).append("\" s=\"17\"><v>").append(res.getNoRegistraSalidaFinal()).append("</v></c>\n");
        sb.append("    </row>\n");

        // Fila 10: Fila de cierre: Días con marcaciones fuera de rango
        sb.append("    <row r=\"").append(tRow + 9).append("\">\n");
        sb.append("      <c r=\"A").append(tRow + 9).append("\" s=\"11\" t=\"inlineStr\"><is><t>Días con marcaciones fuera de rango:</t></is></c>\n");
        sb.append("      <c r=\"B").append(tRow + 9).append("\" s=\"11\"/>\n");
        sb.append("      <c r=\"C").append(tRow + 9).append("\" s=\"12\"><v>").append(res.getDiasConMarcacionesFueraDeRango()).append("</v></c>\n");
        sb.append("    </row>\n");

        sb.append("  </sheetData>\n");
        sb.append("  <mergeCells count=\"6\">\n");
        sb.append("    <mergeCell ref=\"A1:T1\"/>\n");
        sb.append("    <mergeCell ref=\"A").append(tRow).append(":C").append(tRow).append("\"/>\n");
        sb.append("    <mergeCell ref=\"A").append(tRow + 1).append(":B").append(tRow + 1).append("\"/>\n");
        sb.append("    <mergeCell ref=\"A").append(tRow + 2).append(":B").append(tRow + 2).append("\"/>\n");
        sb.append("    <mergeCell ref=\"A").append(tRow + 3).append(":B").append(tRow + 3).append("\"/>\n");
        sb.append("    <mergeCell ref=\"A").append(tRow + 9).append(":B").append(tRow + 9).append("\"/>\n");
        sb.append("  </mergeCells>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    // ── Hoja 2: Planilla de Pagos y Descuentos del Doctor ────────────────────

    private String buildSheet2PlanillaXml(List<LiquidacionPagoDto> planillas) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");

        sb.append("  <cols>\n");
        sb.append("    <col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/>\n");  // ID Empleado
        sb.append("    <col min=\"2\" max=\"2\" width=\"28\" customWidth=\"1\"/>\n");  // Trabajador
        sb.append("    <col min=\"3\" max=\"3\" width=\"24\" customWidth=\"1\"/>\n");  // Departamento
        sb.append("    <col min=\"4\" max=\"4\" width=\"22\" customWidth=\"1\"/>\n");  // Cargo
        sb.append("    <col min=\"5\" max=\"5\" width=\"16\" customWidth=\"1\"/>\n");  // Sueldo Base
        sb.append("    <col min=\"6\" max=\"6\" width=\"14\" customWidth=\"1\"/>\n");  // Días Evaluados
        sb.append("    <col min=\"7\" max=\"7\" width=\"14\" customWidth=\"1\"/>\n");  // Días Asistidos
        sb.append("    <col min=\"8\" max=\"8\" width=\"14\" customWidth=\"1\"/>\n");  // Días Falta
        sb.append("    <col min=\"9\" max=\"9\" width=\"14\" customWidth=\"1\"/>\n");  // Min. Tardanza
        sb.append("    <col min=\"10\" max=\"10\" width=\"14\" customWidth=\"1\"/>\n"); // Hrs. Déficit
        sb.append("    <col min=\"11\" max=\"11\" width=\"14\" customWidth=\"1\"/>\n"); // Hrs. Excedente
        sb.append("    <col min=\"12\" max=\"12\" width=\"18\" customWidth=\"1\"/>\n"); // Dcto. Tardanza
        sb.append("    <col min=\"13\" max=\"13\" width=\"18\" customWidth=\"1\"/>\n"); // Dcto. Déficit
        sb.append("    <col min=\"14\" max=\"14\" width=\"18\" customWidth=\"1\"/>\n"); // Dcto. Faltas
        sb.append("    <col min=\"15\" max=\"15\" width=\"18\" customWidth=\"1\"/>\n"); // Penalidad Omisión
        sb.append("    <col min=\"16\" max=\"16\" width=\"18\" customWidth=\"1\"/>\n"); // Total Descuentos
        sb.append("    <col min=\"17\" max=\"17\" width=\"18\" customWidth=\"1\"/>\n"); // Bonif. Horas Extra
        sb.append("    <col min=\"18\" max=\"18\" width=\"22\" customWidth=\"1\"/>\n"); // NETO A PAGAR
        sb.append("  </cols>\n");

        sb.append("  <sheetData>\n");

        String[] headers = {
                "ID Empleado", "Trabajador", "Departamento / Meta", "Cargo",
                "Sueldo Base (S/)", "Días Evaluados", "Días Asistidos", "Días Falta",
                "Min. Tardanza", "Hrs. Déficit", "Hrs. Excedente", "Dcto. Tardanza (S/)",
                "Dcto. Déficit (S/)", "Dcto. Faltas (S/)", "Penalidad Omisión (S/)",
                "Total Descuentos (S/)", "Bonif. Horas Extra (S/)", "NETO A PAGAR (S/)"
        };

        sb.append("    <row r=\"1\">\n");
        for (int i = 0; i < headers.length; i++) {
            String col = getColLetter(i + 1);
            sb.append("      <c r=\"").append(col).append("1\" s=\"1\" t=\"inlineStr\"><is><t>")
                    .append(escapeXml(headers[i])).append("</t></is></c>\n");
        }
        sb.append("    </row>\n");

        int rowIdx = 2;
        for (LiquidacionPagoDto p : planillas) {
            sb.append("    <row r=\"").append(rowIdx).append("\">\n");

            // A: ID
            appendCellStr(sb, "A", rowIdx, 0, p.getEmpId() != null ? p.getEmpId() : "");
            // B: Trabajador
            appendCellStr(sb, "B", rowIdx, 0, p.getNombreCompleto() != null ? p.getNombreCompleto() : "");
            // C: Departamento
            appendCellStr(sb, "C", rowIdx, 0, p.getDepartamento() != null ? p.getDepartamento() : "");
            // D: Cargo
            appendCellStr(sb, "D", rowIdx, 0, p.getCargo() != null ? p.getCargo() : "Personal");
            // E: Sueldo Base
            appendCellNum(sb, "E", rowIdx, 2, p.getSueldoMensual() != null ? p.getSueldoMensual() : 0);
            // F: Días Evaluados
            appendCellNum(sb, "F", rowIdx, 0, p.getDiasEvaluados());
            // G: Días Asistidos
            appendCellNum(sb, "G", rowIdx, 0, p.getDiasConRegistro());
            // H: Días Falta
            appendCellNum(sb, "H", rowIdx, 0, p.getDiasFalta());
            // I: Min. Tardanza
            appendCellNum(sb, "I", rowIdx, 0, p.getMinutosTardanzaNetosNoJustificados());
            // J: Hrs. Déficit
            appendCellNum(sb, "J", rowIdx, 2, p.getHorasDeficitNetas());
            // K: Hrs. Excedente
            appendCellNum(sb, "K", rowIdx, 2, p.getTotalHorasExcedente());
            // L: Dcto. Tardanza
            appendCellNum(sb, "L", rowIdx, 2, p.getDescuentoTardanzas() != null ? p.getDescuentoTardanzas() : 0);
            // M: Dcto. Déficit
            appendCellNum(sb, "M", rowIdx, 2, p.getDescuentoDeficitHoras() != null ? p.getDescuentoDeficitHoras() : 0);
            // N: Dcto. Faltas
            appendCellNum(sb, "N", rowIdx, 2, p.getDescuentoFaltas() != null ? p.getDescuentoFaltas() : 0);
            // O: Penalidad Omisión
            appendCellNum(sb, "O", rowIdx, 2,
                    p.getPenalidadOmisionMarcado() != null ? p.getPenalidadOmisionMarcado() : 0);

            // P: Total Descuentos (Fórmula SUM(L:O))
            String descFormula = String.format("SUM(L%d:O%d)", rowIdx, rowIdx);
            Number descVal = p.getTotalDescuentos() != null ? p.getTotalDescuentos() : 0;
            appendCellFormulaNum(sb, "P", rowIdx, 2, descFormula, descVal);

            // Q: Bonif. Horas Extra
            appendCellNum(sb, "Q", rowIdx, 2,
                    p.getBonificacionHorasExtra() != null ? p.getBonificacionHorasExtra() : 0);

            // R: NETO A PAGAR (Fórmula MAX(0, E - P + Q))
            String netoFormula = String.format("MAX(0,E%d-P%d+Q%d)", rowIdx, rowIdx, rowIdx);
            Number netoVal = p.getNetoAPagar() != null ? p.getNetoAPagar() : 0;
            appendCellFormulaNum(sb, "R", rowIdx, 4, netoFormula, netoVal);

            sb.append("    </row>\n");
            rowIdx++;
        }

        sb.append("  </sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    // ── Hoja 3: Glosario Técnico y Reglas de Negocio ─────────────────────────

    private String buildSheet3GlosarioXml() {
        StringBuilder sb = new StringBuilder(32 * 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");

        sb.append("  <cols>\n");
        sb.append("    <col min=\"1\" max=\"1\" width=\"15\" customWidth=\"1\"/>\n"); // Columna
        sb.append("    <col min=\"2\" max=\"2\" width=\"28\" customWidth=\"1\"/>\n"); // Concepto
        sb.append("    <col min=\"3\" max=\"3\" width=\"40\" customWidth=\"1\"/>\n"); // Fórmula Excel
        sb.append("    <col min=\"4\" max=\"4\" width=\"36\" customWidth=\"1\"/>\n"); // Motor Java
        sb.append("    <col min=\"5\" max=\"5\" width=\"65\" customWidth=\"1\"/>\n"); // Fundamento Técnico
        sb.append("  </cols>\n");

        sb.append("  <sheetData>\n");

        String[] headers = {
                "Columna", "Concepto / Campo", "Fórmula Excel Corregida (Auditoría PRORRIDRE)",
                "Equivalente en Motor Java (AttendanceEngineService)", "Regla de Negocio y Fundamento Técnico PRORRIDRE"
        };

        sb.append("    <row r=\"1\">\n");
        for (int i = 0; i < headers.length; i++) {
            String col = getColLetter(i + 1);
            sb.append("      <c r=\"").append(col).append("1\" s=\"1\" t=\"inlineStr\"><is><t>")
                    .append(escapeXml(headers[i])).append("</t></is></c>\n");
        }
        sb.append("    </row>\n");

        String[][] filasGlosario = {
                {
                        "Columna I",
                        "Hora de Entrada",
                        "Ventana oficial: 06:00:00 a 08:10:00 (límite con tolerancia). Hasta 12:49:59 computa tardanza.",
                        "AttendanceEngineService.asignarVentanasHorarias()",
                        "Filtro antirrebote de 300s. Tolerancia de 10 minutos (hasta 08:10:00) protegida sin descuento. En Sábados es el único pilar obligatorio."
                },
                {
                        "Columna J - K",
                        "Salida y Retorno de Refrigerio",
                        "J: 12:50:00 - 13:30:00 | K: 13:45:00 - 14:10:00",
                        "AttendanceEngineService.asignarVentanasHorarias()",
                        "CERO RELLENO FICTICIO: Si el trabajador omitió timbrar, las columnas quedan vacías (null). En Sábados no se exige refrigerio ni genera omisión."
                },
                {
                        "Columna L",
                        "Hora de Salida",
                        "Ventana L-V: 17:00:00 a 23:59:59. En Sábados: informativo sin error.",
                        "AttendanceEngineService.asignarVentanasHorarias()",
                        "Captura el término efectivo de la jornada. Marcaciones antes de las 17:00:00 (ej. 16:59:24) van a marcaciones fuera de rango y generan observación."
                },
                {
                        "Columna M",
                        "Condición de Ingreso",
                        "=SI(O(ESBLANCO(I2);I2=\"\");\"Sin Marcación\";SI(I2<=NSHORA(8;10;0);\"A Tiempo\";\"Tardanza\"))",
                        "AttendanceEngineService.evaluarCondicionIngresoYTardanza()",
                        "Ingreso hasta 08:10:00 clasifica como A Tiempo (tolerancia no afecta remuneración). Pasadas las 08:10:00 clasifica como Tardanza."
                },
                {
                        "Columna N",
                        "Minutos de Tardanza",
                        "=SI(O(ESBLANCO(I2);I2=\"\";I2<=NSHORA(8;10;0));0;REDONDEAR((I2-NSHORA(8;10;0))*1440;0))",
                        "AttendanceEngineService.evaluarCondicionIngresoYTardanza()",
                        "Tolerancia de 10 min: hasta 08:10:00 la tardanza es 0. Pasadas las 08:10:00 se computan exclusivamente los minutos que exceden dicho límite."
                },
                {
                        "Columna O",
                        "Jornada Efectiva Laborada",
                        "=SI(O(I2=\"\";J2=\"\";K2=\"\";L2=\"\");\"\";(J2-I2)+(L2-K2))",
                        "AttendanceEngineService.calcularJornadasYBalance()",
                        "Solo se calcula si los 4 pilares están registrados (L-V). Si falta alguno, queda vacío evitando cálculos erróneos. Sábados: no computable (vacío)."
                },
                {
                        "Columna P",
                        "Jornada Reglamentaria (Norma)",
                        "L-V: 08:00:00 | Sábados: --",
                        "AttendanceEngineService.JORNADA_NORMA_HORAS = 8.0",
                        "Jornada estándar legal de 8 horas diarias de labor efectiva de Lunes a Viernes. Sábados voluntarios no computan en horas semanales."
                },
                {
                        "Columna Q",
                        "Balance de Jornada (+Excedente / -Déficit)",
                        "=SI(O2=\"\";\"\";REDONDEAR((O2-NSHORA(8;0;0))*24;2))",
                        "AttendanceEngineService.calcularJornadasYBalance()",
                        "Expresado en horas decimales con base 8 horas exactas. Solo se calcula cuando la jornada efectiva está completa. Sábados queda vacío."
                },
                {
                        "Columna R",
                        "Estado de Cumplimiento",
                        "=SI(O(I2=\"\";J2=\"\";K2=\"\";L2=\"\");\"Incompleto (Falta Marcación)\";SI(Q2>0,08;\"Excedente de Jornada\";SI(Q2<-0,08;\"Déficit de Jornada\";\"Jornada Reglamentaria\")))",
                        "AttendanceEngineService.evaluarEstadoCumplimiento()",
                        "Si falta algún pilar en L-V, el estado es Incompleto (NUNCA Excedente). Sábados con ingreso clasifican como 'Jornada Sabatina'."
                },
                {
                        "Columna S - T",
                        "Auditoría y Observaciones",
                        "S: Justificación Doctor | T: NO REGISTRA ... / SIN INCIDENCIAS",
                        "PayrollService y AttendanceEngineService",
                        "T evalúa independientemente cada pilar faltante y marcaciones fuera de rango unidas por '; '. S permite justificaciones que anulan tardanzas y déficits."
                }
        };

        int rowIdx = 2;
        for (String[] fila : filasGlosario) {
            sb.append("    <row r=\"").append(rowIdx).append("\">\n");
            appendCellStr(sb, "A", rowIdx, 3, fila[0]);
            appendCellStr(sb, "B", rowIdx, 0, fila[1]);
            appendCellStr(sb, "C", rowIdx, 0, fila[2]);
            appendCellStr(sb, "D", rowIdx, 0, fila[3]);
            appendCellStr(sb, "E", rowIdx, 0, fila[4]);
            sb.append("    </row>\n");
            rowIdx++;
        }

        sb.append("  </sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    // ── Utilidades de Celda XML ─────────────────────────────────────────────

    private static void appendCellStr(StringBuilder sb, String col, int row, int style, String val) {
        sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style)
                .append("\" t=\"inlineStr\"><is><t>").append(escapeXml(val)).append("</t></is></c>\n");
    }

    private static void appendCellNum(StringBuilder sb, String col, int row, int style, Number val) {
        sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"><v>")
                .append(val != null ? val.toString() : "0").append("</v></c>\n");
    }

    private static void appendCellFormulaNum(StringBuilder sb, String col, int row, int style, String formula,
            Number cachedVal) {
        sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"><f>")
                .append(escapeXml(formula)).append("</f><v>").append(cachedVal != null ? cachedVal.toString() : "0")
                .append("</v></c>\n");
    }

    private static void appendCellFormulaStr(StringBuilder sb, String col, int row, int style, String formula,
            String cachedVal) {
        sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style)
                .append("\" t=\"str\"><f>").append(escapeXml(formula)).append("</f><v>")
                .append(escapeXml(cachedVal)).append("</v></c>\n");
    }

    private static void appendCellTime(StringBuilder sb, String col, int row, int style, java.time.LocalTime time) {
        if (time == null) {
            sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"/>\n");
        } else {
            double fraction = time.toSecondOfDay() / 86400.0;
            sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"><v>")
                    .append(String.format(java.util.Locale.US, "%.10f", fraction)).append("</v></c>\n");
        }
    }

    private static void appendCellFormulaTime(StringBuilder sb, String col, int row, int style, String formula,
            Double cachedFraction) {
        if (cachedFraction == null) {
            sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"><f>")
                    .append(escapeXml(formula)).append("</f></c>\n");
        } else {
            sb.append("      <c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"><f>")
                    .append(escapeXml(formula)).append("</f><v>")
                    .append(String.format(java.util.Locale.US, "%.10f", cachedFraction)).append("</v></c>\n");
        }
    }

    private static String getColLetter(int colNumber) {
        StringBuilder sb = new StringBuilder();
        while (colNumber > 0) {
            int rem = (colNumber - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            colNumber = (colNumber - 1) / 26;
        }
        return sb.toString();
    }

    private static String escapeXml(String text) {
        if (text == null)
            return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    if (c < 0x20 && c != '\t' && c != '\r' && c != '\n') {
                        // Omitir caracteres de control no válidos en XML
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
