package pe.gob.prorridre.asistencia.service;

import pe.gob.prorridre.asistencia.domain.entity.Marcacion;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.MarcacionRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.ImportResultDto;
import pe.gob.prorridre.asistencia.dto.RegistroAsistenciaDto;
import pe.gob.prorridre.asistencia.dto.ResumenKpiDto;
import pe.gob.prorridre.asistencia.model.Departamento;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Servicio importador y sincronizador de registros de asistencia.
 * Aplica la política de ENRIQUECIMIENTO DE DATOS MAESTROS:
 * - NO confía en las columnas B (Nombres), C (Apellidos) ni E (Departamento) de los archivos/marcaciones.
 * - Con el empId realiza un lookup en el catálogo maestro de Empleado y Departamento jerárquico.
 * - Asigna nombres reales y departamento institucional canónico.
 * - Si un empleado no existe, lo registra temporalmente como DESCONOCIDO y emite alertas.
 */
@Service
public class ExcelCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelCsvImportService.class);

    private final MarcacionRepository marcacionRepo;
    private final RegistroAsistenciaRepository registroRepo;
    private final EmpleadoRepository empleadoRepo;
    private final DepartamentoRepository departamentoRepo;
    private final AttendanceEngineService engineService;

    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FMT_FECHA_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ExcelCsvImportService(MarcacionRepository marcacionRepo,
                                 RegistroAsistenciaRepository registroRepo,
                                 EmpleadoRepository empleadoRepo,
                                 DepartamentoRepository departamentoRepo,
                                 AttendanceEngineService engineService) {
        this.marcacionRepo = marcacionRepo;
        this.registroRepo = registroRepo;
        this.empleadoRepo = empleadoRepo;
        this.departamentoRepo = departamentoRepo;
        this.engineService = engineService;
    }

    /**
     * Sincroniza y recalcula todos los registros de asistencia agrupando las
     * marcaciones crudas de la base de datos local por empleado y fecha,
     * enriqueciéndolas automáticamente con el catálogo maestro de empleados.
     */
    @Transactional
    public ImportResultDto sincronizarDesdeMarcaciones() {
        log.info("[Importador] Iniciando sincronización y recálculo desde tabla 'marcacion' con enriquecimiento...");
        List<Marcacion> todas = marcacionRepo.findAllByOrderByEmpCodeAscPunchTimeAsc();

        if (todas.isEmpty()) {
            return new ImportResultDto(true, "No hay marcaciones en la base de datos para sincronizar.", 0, 0, 0, 0);
        }

        // Agrupar por empCode y fecha (LocalDate)
        Map<String, Map<LocalDate, List<Marcacion>>> agrupadas = new LinkedHashMap<>();

        for (Marcacion m : todas) {
            String emp = m.getEmpCode() != null ? m.getEmpCode().trim() : "SIN_ID";
            if (m.getPunchTime() == null) continue;

            LocalDate fecha = m.getPunchTime().toLocalDate();
            agrupadas.computeIfAbsent(emp, k -> new TreeMap<>())
                     .computeIfAbsent(fecha, k -> new ArrayList<>())
                     .add(m);
        }

        int total = 0;
        int insertados = 0;
        int actualizados = 0;
        int errores = 0;

        for (Map.Entry<String, Map<LocalDate, List<Marcacion>>> empEntry : agrupadas.entrySet()) {
            String empId = empEntry.getKey();

            // ENRIQUECIMIENTO CANÓNICO DE DATOS MAESTROS
            Empleado emp = obtenerOEnriquecerEmpleado(empId);

            for (Map.Entry<LocalDate, List<Marcacion>> fechaEntry : empEntry.getValue().entrySet()) {
                total++;
                LocalDate fecha = fechaEntry.getKey();
                List<Marcacion> marcacionesDia = fechaEntry.getValue();

                // Construir cadena de horas crudas
                String horasCrudasStr = marcacionesDia.stream()
                        .map(m -> m.getPunchTime().toLocalTime().format(FMT_HORA))
                        .collect(Collectors.joining(", "));

                try {
                    RegistroAsistencia regCalculado = engineService.calcularRegistro(
                            empId,
                            emp.getNombres(),
                            emp.getApellidos(),
                            emp.getEmpresa(),
                            emp.getDepartamentoNombre(),
                            fecha,
                            horasCrudasStr
                    );

                    Optional<RegistroAsistencia> existenteOpt = registroRepo.findByEmpIdAndFecha(empId, fecha);
                    if (existenteOpt.isPresent()) {
                        RegistroAsistencia existente = existenteOpt.get();
                        copiarPropiedades(regCalculado, existente);
                        registroRepo.save(existente);
                        actualizados++;
                    } else {
                        registroRepo.save(regCalculado);
                        insertados++;
                    }
                } catch (Exception ex) {
                    log.error("[Importador] Error calculando registro para empId={} fecha={}: {}", empId, fecha, ex.getMessage(), ex);
                    errores++;
                }
            }
        }

        String msg = String.format("Sincronización completada: %d días/empleado procesados (%d nuevos, %d actualizados, %d errores).",
                total, insertados, actualizados, errores);
        log.info("[Importador] {}", msg);
        return new ImportResultDto(true, msg, total, insertados, actualizados, errores);
    }

    /**
     * Importa registros desde un archivo subido (CSV o XLSX) aplicando enriquecimiento de maestros.
     */
    @Transactional
    public ImportResultDto importarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ImportResultDto(false, "El archivo proporcionado está vacío.", 0, 0, 0, 1);
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            if (filename.endsWith(".csv") || filename.endsWith(".txt")) {
                return procesarCsv(file.getInputStream());
            } else if (filename.endsWith(".xlsx")) {
                return procesarXlsx(file.getInputStream());
            } else {
                return new ImportResultDto(false, "Formato no compatible. Por favor sube un archivo .CSV o .XLSX", 0, 0, 0, 1);
            }
        } catch (Exception e) {
            log.error("[Importador] Error procesando archivo {}: {}", filename, e.getMessage(), e);
            return new ImportResultDto(false, "Error al procesar el archivo: " + e.getMessage(), 0, 0, 0, 1);
        }
    }

    /**
     * Parsea un archivo CSV delimitado por comas, puntos y comas o tabulaciones.
     * Lee únicamente la Columna A (empId), la Columna F (fecha) y la Columna H (horas crudas),
     * enriqueciendo nombres y departamento desde la tabla maestra.
     */
    public ImportResultDto procesarCsv(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String linea = reader.readLine();
        if (linea == null) {
            return new ImportResultDto(false, "Archivo CSV vacío.", 0, 0, 0, 0);
        }

        char separador = linea.contains(";") ? ';' : (linea.contains("\t") ? '\t' : ',');
        int total = 0, insertados = 0, actualizados = 0, errores = 0;

        // Leer encabezado
        String[] headers = parsearLineaCsv(linea, separador);
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            colIndex.put(headers[i].trim().toLowerCase(), i);
        }

        while ((linea = reader.readLine()) != null) {
            if (linea.trim().isEmpty()) continue;
            String[] cols = parsearLineaCsv(linea, separador);
            total++;

            try {
                // Solo leemos el identificador y las horas de timbrado
                String empId = obtenerValorColumna(cols, colIndex, 0, "empid", "emp_id", "codigo", "código", "id");
                String fechaStr = obtenerValorColumna(cols, colIndex, 5, "fecha", "date");
                String horasCrudas = obtenerValorColumna(cols, colIndex, 7, "horascrudas", "horas_crudas", "marcaciones");

                // Si la columna horasCrudas está vacía pero hay columnas I, J, K, L, concatenarlas
                if (horasCrudas.isEmpty()) {
                    List<String> horasList = new ArrayList<>();
                    for (int idx : new int[]{8, 9, 10, 11}) {
                        if (idx < cols.length && !cols[idx].trim().isEmpty()) {
                            horasList.add(cols[idx].trim());
                        }
                    }
                    horasCrudas = String.join(", ", horasList);
                }

                LocalDate fecha = parsearFechaFlexible(fechaStr);
                if (fecha == null) fecha = LocalDate.now();

                // ENRIQUECIMIENTO CANÓNICO DE MAESTROS
                Empleado emp = obtenerOEnriquecerEmpleado(empId);

                RegistroAsistencia reg = engineService.calcularRegistro(
                        empId,
                        emp.getNombres(),
                        emp.getApellidos(),
                        emp.getEmpresa(),
                        emp.getDepartamentoNombre(),
                        fecha,
                        horasCrudas
                );

                Optional<RegistroAsistencia> existenteOpt = registroRepo.findByEmpIdAndFecha(empId, fecha);
                if (existenteOpt.isPresent()) {
                    RegistroAsistencia existente = existenteOpt.get();
                    copiarPropiedades(reg, existente);
                    registroRepo.save(existente);
                    actualizados++;
                } else {
                    registroRepo.save(reg);
                    insertados++;
                }
            } catch (Exception ex) {
                log.warn("[Importador CSV] Error en línea {}: {}", total, ex.getMessage());
                errores++;
            }
        }

        String msg = String.format("CSV procesado exitosamente: %d filas (%d nuevas, %d actualizadas, %d errores).",
                total, insertados, actualizados, errores);
        return new ImportResultDto(true, msg, total, insertados, actualizados, errores);
    }

    /**
     * Parsea un archivo XLSX estándar en memoria utilizando java.util.zip y XML DOM.
     * Aplica el enriquecimiento canónico de maestros para nombres y departamentos.
     */
    public ImportResultDto procesarXlsx(InputStream is) throws Exception {
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;

        List<String> sharedStrings = new ArrayList<>();
        byte[] sheetXmlBytes = null;

        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.equals("xl/sharedStrings.xml")) {
                sharedStrings = parsearSharedStrings(leerBytes(zis));
            } else if (name.equals("xl/worksheets/sheet1.xml") && sheetXmlBytes == null) {
                sheetXmlBytes = leerBytes(zis);
            }
            zis.closeEntry();
        }

        if (sheetXmlBytes == null) {
            return new ImportResultDto(false, "No se encontró hoja de datos 'sheet1.xml' en el archivo Excel.", 0, 0, 0, 1);
        }

        // Parsear filas de sheet1.xml
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(sheetXmlBytes));

        NodeList rowNodes = doc.getElementsByTagName("row");
        int total = 0, insertados = 0, actualizados = 0, errores = 0;

        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowEl = (Element) rowNodes.item(i);
            int rowNum = Integer.parseInt(rowEl.getAttribute("r"));
            if (rowNum == 1) continue; // Saltar cabecera

            Map<Integer, String> cellValues = new TreeMap<>();
            NodeList cNodes = rowEl.getElementsByTagName("c");
            for (int j = 0; j < cNodes.getLength(); j++) {
                Element cEl = (Element) cNodes.item(j);
                String cellRef = cEl.getAttribute("r"); // ej. "A2", "B2"
                int colIdx = colRefToIndex(cellRef);
                String type = cEl.getAttribute("t");

                NodeList vNodes = cEl.getElementsByTagName("v");
                if (vNodes.getLength() > 0) {
                    String val = vNodes.item(0).getTextContent();
                    if ("s".equals(type)) { // Shared string
                        int sIdx = Integer.parseInt(val);
                        if (sIdx >= 0 && sIdx < sharedStrings.size()) {
                            val = sharedStrings.get(sIdx);
                        }
                    }
                    cellValues.put(colIdx, val);
                }
            }

            if (cellValues.isEmpty()) continue;
            total++;

            try {
                String empId = cellValues.getOrDefault(0, "SIN_ID");
                String fechaStr = cellValues.getOrDefault(5, "");
                String horasCrudas = cellValues.getOrDefault(7, "");

                if (horasCrudas.isEmpty()) {
                    List<String> horasList = new ArrayList<>();
                    for (int idx : new int[]{8, 9, 10, 11}) {
                        String h = cellValues.get(idx);
                        if (h != null && !h.trim().isEmpty()) horasList.add(h.trim());
                    }
                    horasCrudas = String.join(", ", horasList);
                }

                LocalDate fecha = parsearFechaFlexible(fechaStr);
                if (fecha == null) fecha = LocalDate.now();

                // ENRIQUECIMIENTO CANÓNICO DE MAESTROS
                Empleado emp = obtenerOEnriquecerEmpleado(empId);

                RegistroAsistencia reg = engineService.calcularRegistro(
                        empId,
                        emp.getNombres(),
                        emp.getApellidos(),
                        emp.getEmpresa(),
                        emp.getDepartamentoNombre(),
                        fecha,
                        horasCrudas
                );

                Optional<RegistroAsistencia> existenteOpt = registroRepo.findByEmpIdAndFecha(empId, fecha);
                if (existenteOpt.isPresent()) {
                    RegistroAsistencia existente = existenteOpt.get();
                    copiarPropiedades(reg, existente);
                    registroRepo.save(existente);
                    actualizados++;
                } else {
                    registroRepo.save(reg);
                    insertados++;
                }
            } catch (Exception ex) {
                log.warn("[Importador XLSX] Error en fila {}: {}", rowNum, ex.getMessage());
                errores++;
            }
        }

        String msg = String.format("Excel XLSX procesado: %d filas (%d nuevas, %d actualizadas, %d errores).",
                total, insertados, actualizados, errores);
        return new ImportResultDto(true, msg, total, insertados, actualizados, errores);
    }

    /**
     * Resuelve y enriquece un empleado a partir de su ID de biométrico.
     * Si no existe en la BD, lo crea temporalmente como DESCONOCIDO.
     */
    public Empleado obtenerOEnriquecerEmpleado(String empId) {
        if (empId == null || empId.trim().isEmpty()) {
            empId = "SIN_ID";
        }
        final String cleanId = empId.trim();

        return empleadoRepo.findById(cleanId).orElseGet(() -> {
            String depSugerido = "POR ASIGNAR";
            Departamento depto = departamentoRepo.findByNombreIgnoreCase(depSugerido).orElse(null);

            Empleado nuevo = new Empleado(
                    cleanId,
                    "DESCONOCIDO",
                    "EMPLEADO ID " + cleanId,
                    "PRORRIDRE PUNO",
                    depSugerido
            );
            if (depto != null) {
                nuevo.setDepartamento(depto);
            }
            log.warn("[Enriquecimiento] Empleado ID {} no encontrado en catálogo maestro. Creado temporalmente como desconocido.", cleanId);
            return empleadoRepo.save(nuevo);
        });
    }

    /**
     * Devuelve la lista de registros recalculados con filtros opcionales,
     * soportando filtrado por jerarquía departamental completa.
     */
    public List<RegistroAsistenciaDto> obtenerRegistros(LocalDate fecha, String empId, String departamento) {
        List<RegistroAsistencia> entidades;

        if (departamento != null && !departamento.trim().isEmpty()) {
            // Resolver descendientes del departamento para filtro anidado de forma segura en memoria
            Set<String> deptsPermitidos = departamentoRepo.obtenerNombresDescendientesDe(departamento.trim());
            final Set<String> filtroFinal = deptsPermitidos;
            if (fecha != null) {
                entidades = registroRepo.findByFechaOrderByDepartamentoAscApellidosAsc(fecha).stream()
                        .filter(r -> r.getDepartamento() != null && filtroFinal.contains(r.getDepartamento().trim().toUpperCase()))
                        .collect(Collectors.toList());
            } else {
                entidades = registroRepo.findAll().stream()
                        .filter(r -> r.getDepartamento() != null && filtroFinal.contains(r.getDepartamento().trim().toUpperCase()))
                        .sorted(Comparator.comparing(RegistroAsistencia::getFecha).reversed()
                                .thenComparing(RegistroAsistencia::getEmpId))
                        .limit(500)
                        .collect(Collectors.toList());
            }
        } else if (fecha != null) {
            entidades = registroRepo.findByFechaOrderByDepartamentoAscApellidosAsc(fecha);
        } else if (empId != null && !empId.trim().isEmpty()) {
            entidades = registroRepo.findByEmpIdOrderByFechaDesc(empId.trim());
        } else {
            entidades = registroRepo.findAll().stream()
                    .sorted(Comparator.comparing(RegistroAsistencia::getFecha).reversed()
                            .thenComparing(RegistroAsistencia::getEmpId))
                    .limit(200)
                    .collect(Collectors.toList());
        }

        return entidades.stream().map(this::convertirADto).collect(Collectors.toList());
    }

    /**
     * Calcula métricas y KPIs de asistencia.
     */
    public ResumenKpiDto calcularResumen(LocalDate fecha) {
        LocalDate fechaTarget = (fecha != null) ? fecha :
                registroRepo.findDistinctFechas().stream().findFirst().orElse(LocalDate.now());

        List<RegistroAsistencia> lista = registroRepo.findByFechaOrderByDepartamentoAscApellidosAsc(fechaTarget);

        ResumenKpiDto kpi = new ResumenKpiDto();
        kpi.setFecha(fechaTarget);
        kpi.setTotalRegistros(lista.size());

        long aTiempo = 0, tardanzas = 0, omision = 0, incompletos = 0;
        long excedente = 0, deficit = 0, reglamentaria = 0;
        long totalMinTardanza = 0;
        double totalHoras = 0.0;

        for (RegistroAsistencia r : lista) {
            if ("A Tiempo".equalsIgnoreCase(r.getCondicionIngreso())) aTiempo++;
            else if ("Tardanza".equalsIgnoreCase(r.getCondicionIngreso())) tardanzas++;
            else if (r.getCondicionIngreso() != null && r.getCondicionIngreso().contains("Omisión")) omision++;

            if (r.getEstadoCumplimiento() != null && r.getEstadoCumplimiento().startsWith("Incompleto")) incompletos++;
            else if ("Excedente de Jornada".equalsIgnoreCase(r.getEstadoCumplimiento())) excedente++;
            else if ("Déficit de Jornada".equalsIgnoreCase(r.getEstadoCumplimiento())) deficit++;
            else if ("Jornada Reglamentaria".equalsIgnoreCase(r.getEstadoCumplimiento())) reglamentaria++;

            if (r.getMinutosTardanza() != null) totalMinTardanza += r.getMinutosTardanza();
            if (r.getHorasEfectivasDecimal() != null) totalHoras += r.getHorasEfectivasDecimal();
        }

        kpi.setaTiempo(aTiempo);
        kpi.setTardanzas(tardanzas);
        kpi.setOmisionEntrada(omision);
        kpi.setIncompletos(incompletos);
        kpi.setExcedenteJornada(excedente);
        kpi.setDeficitJornada(deficit);
        kpi.setJornadaReglamentaria(reglamentaria);
        kpi.setTotalMinutosTardanza(totalMinTardanza);
        kpi.setTotalHorasEfectivas(Math.round(totalHoras * 100.0) / 100.0);
        kpi.setPromedioHorasEfectivas(lista.isEmpty() ? 0.0 : Math.round((totalHoras / lista.size()) * 100.0) / 100.0);

        return kpi;
    }

    public List<String> obtenerDepartamentos() {
        Set<String> depts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        depts.addAll(departamentoRepo.findAll().stream().map(Departamento::getNombre).collect(Collectors.toList()));
        depts.addAll(registroRepo.findDistinctDepartamentos());
        return new ArrayList<>(depts);
    }

    public List<LocalDate> obtenerFechasDisponibles() {
        return registroRepo.findDistinctFechas();
    }

    /* -----------------------------------------------------------------------
     * Métodos Auxiliares
     * --------------------------------------------------------------------- */

    private RegistroAsistenciaDto convertirADto(RegistroAsistencia e) {
        RegistroAsistenciaDto dto = new RegistroAsistenciaDto();
        dto.setId(e.getId());
        dto.setEmpId(e.getEmpId());
        dto.setNombres(e.getNombres());
        dto.setApellidos(e.getApellidos());
        dto.setNombreCompleto(((e.getApellidos() != null ? e.getApellidos() : "") + " " +
                (e.getNombres() != null ? e.getNombres() : "")).trim());
        dto.setEmpresa(e.getEmpresa());
        dto.setDepartamento(e.getDepartamento());
        dto.setFecha(e.getFecha());
        dto.setNumMarcaciones(e.getNumMarcaciones());
        dto.setHorasCrudas(e.getHorasCrudas());
        dto.setHoraEntrada(e.getHoraEntrada());
        dto.setInicioAlmuerzo(e.getInicioAlmuerzo());
        dto.setFinAlmuerzo(e.getFinAlmuerzo());
        dto.setHoraSalida(e.getHoraSalida());
        dto.setCondicionIngreso(e.getCondicionIngreso());
        dto.setMinutosTardanza(e.getMinutosTardanza());
        dto.setJornadaEfectiva(e.getJornadaEfectiva());
        dto.setHorasEfectivasDecimal(e.getHorasEfectivasDecimal());
        dto.setJornadaNorma(e.getJornadaNorma());
        dto.setHorasNormaDecimal(e.getHorasNormaDecimal());
        dto.setBalanceJornada(e.getBalanceJornada());
        dto.setEstadoCumplimiento(e.getEstadoCumplimiento());
        dto.setAlmuerzoImputado(e.getAlmuerzoImputado());
        dto.setEstadoJustificacion(e.getEstadoJustificacion());
        dto.setObservacionDoctor(e.getObservacionDoctor());
        dto.setOtrasMarcaciones(e.getOtrasMarcaciones());
        dto.setMinutosTardanzaOriginal(e.getMinutosTardanzaOriginal());
        dto.setBalanceJornadaOriginal(e.getBalanceJornadaOriginal());
        return dto;
    }

    private void copiarPropiedades(RegistroAsistencia origen, RegistroAsistencia destino) {
        destino.setNombres(origen.getNombres());
        destino.setApellidos(origen.getApellidos());
        destino.setEmpresa(origen.getEmpresa());
        destino.setDepartamento(origen.getDepartamento());
        destino.setNumMarcaciones(origen.getNumMarcaciones());
        destino.setHorasCrudas(origen.getHorasCrudas());
        destino.setHoraEntrada(origen.getHoraEntrada());
        destino.setInicioAlmuerzo(origen.getInicioAlmuerzo());
        destino.setFinAlmuerzo(origen.getFinAlmuerzo());
        destino.setHoraSalida(origen.getHoraSalida());
        destino.setOtrasMarcaciones(origen.getOtrasMarcaciones());
        destino.setCondicionIngreso(origen.getCondicionIngreso());
        destino.setMinutosTardanza(origen.getMinutosTardanza());
        destino.setJornadaEfectiva(origen.getJornadaEfectiva());
        destino.setHorasEfectivasDecimal(origen.getHorasEfectivasDecimal());
        destino.setJornadaNorma(origen.getJornadaNorma());
        destino.setHorasNormaDecimal(origen.getHorasNormaDecimal());
        destino.setBalanceJornada(origen.getBalanceJornada());
        destino.setEstadoCumplimiento(origen.getEstadoCumplimiento());
        destino.setAlmuerzoImputado(origen.getAlmuerzoImputado());
        if (destino.getObservacionDoctor() == null || destino.getObservacionDoctor().isEmpty()
                || destino.getObservacionDoctor().startsWith("NO REGISTRA")
                || destino.getObservacionDoctor().startsWith("JORNADA SABATINA")
                || "SIN INCIDENCIAS".equals(destino.getObservacionDoctor())) {
            destino.setObservacionDoctor(origen.getObservacionDoctor());
        }
        if (destino.getEstadoJustificacion() == null || "NINGUNO".equalsIgnoreCase(destino.getEstadoJustificacion())) {
            destino.setMinutosTardanza(origen.getMinutosTardanza());
            destino.setBalanceJornada(origen.getBalanceJornada());
        } else {
            destino.setMinutosTardanzaOriginal(origen.getMinutosTardanza());
            destino.setBalanceJornadaOriginal(origen.getBalanceJornada());
        }
    }

    private LocalDate parsearFechaFlexible(String fechaStr) {
        if (fechaStr == null || fechaStr.trim().isEmpty()) return null;
        String s = fechaStr.trim();
        try {
            if (s.matches("^\\d{4}-\\d{2}-\\d{2}$")) return LocalDate.parse(s, FMT_FECHA_ISO);
            if (s.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
                String[] p = s.split("/");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
            if (s.matches("^\\d{1,2}/\\d{1,2}/\\d{4}$")) {
                String[] p = s.split("/");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
            if (s.matches("^\\d{5}$")) {
                long serial = Long.parseLong(s);
                return LocalDate.of(1899, 12, 30).plusDays(serial);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String[] parsearLineaCsv(String linea, char sep) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == sep && !inQuotes) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString().trim());
        return result.toArray(new String[0]);
    }

    private String obtenerValorColumna(String[] cols, Map<String, Integer> colIndex, int fallbackIdx, String... keys) {
        for (String k : keys) {
            if (k != null && colIndex.containsKey(k.toLowerCase())) {
                int idx = colIndex.get(k.toLowerCase());
                if (idx < cols.length) return cols[idx].trim();
            }
        }
        if (fallbackIdx >= 0 && fallbackIdx < cols.length) {
            return cols[fallbackIdx].trim();
        }
        return "";
    }

    private int colRefToIndex(String cellRef) {
        int idx = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (Character.isLetter(c)) {
                idx = idx * 26 + (Character.toUpperCase(c) - 'A' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, idx - 1);
    }

    private List<String> parsearSharedStrings(byte[] bytes) {
        List<String> list = new ArrayList<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            NodeList siList = doc.getElementsByTagName("si");
            for (int i = 0; i < siList.getLength(); i++) {
                Element siEl = (Element) siList.item(i);
                NodeList tList = siEl.getElementsByTagName("t");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < tList.getLength(); j++) {
                    sb.append(tList.item(j).getTextContent());
                }
                list.add(sb.toString());
            }
        } catch (Exception e) {
            log.warn("[XLSX] Error parseando sharedStrings: {}", e.getMessage());
        }
        return list;
    }

    private byte[] leerBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
}
