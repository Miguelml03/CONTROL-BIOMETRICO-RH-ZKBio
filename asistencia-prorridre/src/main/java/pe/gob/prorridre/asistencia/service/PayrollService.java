package pe.gob.prorridre.asistencia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoConfigFinancieraRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.JustificacionAuditoriaRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.ConfigFinancieraRequestDto;
import pe.gob.prorridre.asistencia.dto.JustificarRequestDto;
import pe.gob.prorridre.asistencia.dto.LiquidacionPagoDto;
import pe.gob.prorridre.asistencia.model.Departamento;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.EmpleadoConfigFinanciera;
import pe.gob.prorridre.asistencia.model.JustificacionAuditoria;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Motor Financiero de Pagos, Descuentos y Auditoría del Doctor.
 * Permite liquidar haberes mensuales, calcular deducciones por tardanzas,
 * déficit y faltas, y autorizar justificaciones con efecto inmediato.
 */
@Service
@Transactional
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final RegistroAsistenciaRepository registroRepo;
    private final EmpleadoRepository empleadoRepo;
    private final DepartamentoRepository departamentoRepo;
    private final EmpleadoConfigFinancieraRepository configRepo;
    private final JustificacionAuditoriaRepository justificacionRepo;

    public PayrollService(RegistroAsistenciaRepository registroRepo,
                          EmpleadoRepository empleadoRepo,
                          DepartamentoRepository departamentoRepo,
                          EmpleadoConfigFinancieraRepository configRepo,
                          JustificacionAuditoriaRepository justificacionRepo) {
        this.registroRepo = registroRepo;
        this.empleadoRepo = empleadoRepo;
        this.departamentoRepo = departamentoRepo;
        this.configRepo = configRepo;
        this.justificacionRepo = justificacionRepo;
    }

    /**
     * Liquida la planilla completa para todos los empleados en un rango de fechas.
     */
    @Transactional(readOnly = true)
    public List<LiquidacionPagoDto> liquidarPlanilla(LocalDate fechaInicio,
                                                      LocalDate fechaFin,
                                                      String departamento,
                                                      String empId) {

        List<LocalDate> fechasDisponibles = registroRepo.findDistinctFechas();
        LocalDate desde = fechaInicio != null ? fechaInicio : (fechasDisponibles.isEmpty() ? LocalDate.now().withDayOfMonth(1) : fechasDisponibles.get(fechasDisponibles.size() - 1));
        LocalDate hasta = fechaFin != null ? fechaFin : (fechasDisponibles.isEmpty() ? LocalDate.now() : fechasDisponibles.get(0));

        if (desde.isAfter(hasta)) {
            LocalDate tmp = desde;
            desde = hasta;
            hasta = tmp;
        }

        List<Empleado> empleados = obtenerEmpleados(departamento, empId);
        List<LiquidacionPagoDto> liquidaciones = new ArrayList<>();

        for (Empleado emp : empleados) {
            liquidaciones.add(calcularLiquidacionEmpleado(emp, desde, hasta));
        }

        return liquidaciones;
    }

    /**
     * Liquida la planilla individual de un trabajador específico.
     */
    @Transactional(readOnly = true)
    public LiquidacionPagoDto liquidarEmpleadoIndividual(String empId, LocalDate fechaInicio, LocalDate fechaFin) {
        Empleado emp = empleadoRepo.findById(empId)
                .orElseGet(() -> {
                    // Si no está en empleado, buscar en registro_asistencia
                    List<RegistroAsistencia> regs = registroRepo.findByEmpIdOrderByFechaDesc(empId);
                    if (!regs.isEmpty()) {
                        RegistroAsistencia r = regs.get(0);
                        return new Empleado(empId, r.getNombres(), r.getApellidos(), r.getEmpresa(), r.getDepartamento());
                    }
                    return new Empleado(empId, "EMPLEADO", empId, "PRORRIDRE", "GENERAL");
                });

        List<LocalDate> fechasDisponibles = registroRepo.findDistinctFechas();
        LocalDate desde = fechaInicio != null ? fechaInicio : (fechasDisponibles.isEmpty() ? LocalDate.now().withDayOfMonth(1) : fechasDisponibles.get(fechasDisponibles.size() - 1));
        LocalDate hasta = fechaFin != null ? fechaFin : (fechasDisponibles.isEmpty() ? LocalDate.now() : fechasDisponibles.get(0));

        return calcularLiquidacionEmpleado(emp, desde, hasta);
    }

    /**
     * Acción Rápida del Doctor: Justifica una marcación o cambia su estado.
     * Recalcula tardanzas, balance y actualiza la auditoría.
     */
    public RegistroAsistencia justificarRegistro(Long registroId, JustificarRequestDto req) {
        RegistroAsistencia reg = registroRepo.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro de asistencia no encontrado con ID: " + registroId));

        // Inicializar valores originales de resguardo si es la primera justificación
        if (reg.getMinutosTardanzaOriginal() == null) {
            reg.setMinutosTardanzaOriginal(reg.getMinutosTardanza());
        }
        if (reg.getBalanceJornadaOriginal() == null) {
            reg.setBalanceJornadaOriginal(reg.getBalanceJornada());
        }

        String tipoStr = req.getTipoJustificacion() != null ? req.getTipoJustificacion().trim().toUpperCase() : "APLICAR_DESCUENTO";
        JustificacionAuditoria.TipoJustificacion tipo;
        try {
            tipo = JustificacionAuditoria.TipoJustificacion.valueOf(tipoStr);
        } catch (IllegalArgumentException e) {
            tipo = JustificacionAuditoria.TipoJustificacion.APLICAR_DESCUENTO;
        }

        long minutosExonerados = 0L;
        double horasExoneradas = 0.0;

        if (tipo == JustificacionAuditoria.TipoJustificacion.APLICAR_DESCUENTO) {
            // Revertir a valores originales calculados por el biométrico
            reg.setMinutosTardanza(reg.getMinutosTardanzaOriginal() != null ? reg.getMinutosTardanzaOriginal() : 0L);
            reg.setBalanceJornada(reg.getBalanceJornadaOriginal());
            reg.setEstadoJustificacion("APLICAR_DESCUENTO");
            reg.setObservacionDoctor(req.getObservacion() != null ? req.getObservacion() : "Descuento ratificado por el Doctor.");
        } else {
            // Exonerar tardanza
            minutosExonerados = reg.getMinutosTardanzaOriginal() != null ? reg.getMinutosTardanzaOriginal() : reg.getMinutosTardanza();
            reg.setMinutosTardanza(0L);

            // Exonerar déficit de jornada si existiera
            Double balOrig = reg.getBalanceJornadaOriginal() != null ? reg.getBalanceJornadaOriginal() : reg.getBalanceJornada();
            if (balOrig != null && balOrig < 0) {
                horasExoneradas = Math.abs(balOrig);
                reg.setBalanceJornada(0.0);
                reg.setEstadoCumplimiento("Jornada Reglamentaria (Justificado)");
            }

            reg.setEstadoJustificacion(tipo.name());
            reg.setObservacionDoctor(req.getObservacion() != null ? req.getObservacion() : "Justificado según " + tipo.name());
        }

        // Crear registro de auditoría
        JustificacionAuditoria aud = new JustificacionAuditoria();
        aud.setRegistroId(reg.getId());
        aud.setEmpId(reg.getEmpId());
        aud.setTipoJustificacion(tipo);
        aud.setObservacion(req.getObservacion());
        aud.setMinutosExonerados(minutosExonerados);
        aud.setHorasExoneradas(horasExoneradas);
        aud.setAplicadoPor(req.getAplicadoPor() != null ? req.getAplicadoPor() : "DOCTOR");
        aud.setVigente(true);
        justificacionRepo.save(aud);

        return registroRepo.save(reg);
    }

    /**
     * Determina si un empleado es Practicante (no percibe salario ni se le aplican deducciones salariales).
     */
    public boolean esPracticante(String empId) {
        if (empId == null) return false;
        return empleadoRepo.findById(empId).map(this::esPracticante).orElse(false);
    }

    public boolean esPracticante(Empleado emp) {
        if (emp == null) return false;
        String depto = emp.getDepartamentoNombre() != null ? emp.getDepartamentoNombre().toUpperCase() : "";
        String cargo = emp.getCargo() != null ? emp.getCargo().toUpperCase() : "";
        return depto.contains("PRACTICANTE") || cargo.contains("PRACTICANTE");
    }

    /**
     * Obtiene la configuración financiera del trabajador o devuelve los valores por defecto.
     * Si es practicante, el sueldo base es S/ 0.00.
     */
    public EmpleadoConfigFinanciera obtenerOConfigurarFinanciera(String empId) {
        boolean practicante = esPracticante(empId);
        return configRepo.findByEmpId(empId)
                .map(conf -> {
                    if (practicante && conf.getSueldoMensual().compareTo(BigDecimal.ZERO) > 0) {
                        conf.setSueldoMensual(BigDecimal.ZERO);
                        return configRepo.save(conf);
                    }
                    return conf;
                })
                .orElseGet(() -> {
                    EmpleadoConfigFinanciera conf = new EmpleadoConfigFinanciera(empId);
                    if (practicante) {
                        conf.setSueldoMensual(BigDecimal.ZERO);
                    }
                    return configRepo.save(conf);
                });
    }

    /**
     * Guarda o edita la configuración financiera de un empleado.
     */
    public EmpleadoConfigFinanciera actualizarConfigFinanciera(ConfigFinancieraRequestDto req) {
        EmpleadoConfigFinanciera conf = configRepo.findByEmpId(req.getEmpId())
                .orElseGet(() -> new EmpleadoConfigFinanciera(req.getEmpId()));

        if (req.getSueldoMensual() != null) conf.setSueldoMensual(req.getSueldoMensual());
        if (req.getDiasBaseMes() != null && req.getDiasBaseMes() > 0) conf.setDiasBaseMes(req.getDiasBaseMes());
        if (req.getHorasBaseDia() != null && req.getHorasBaseDia() > 0) conf.setHorasBaseDia(req.getHorasBaseDia());
        if (req.getMultaOmision() != null) conf.setMultaOmision(req.getMultaOmision());
        if (req.getExcedenteCompensaDeficit() != null) conf.setExcedenteCompensaDeficit(req.getExcedenteCompensaDeficit());
        if (req.getCargo() != null) conf.setCargo(req.getCargo());
        if (req.getActualizadoPor() != null) conf.setActualizadoPor(req.getActualizadoPor());

        return configRepo.save(conf);
    }

    /**
     * Lista todas las configuraciones financieras guardadas.
     */
    @Transactional(readOnly = true)
    public List<EmpleadoConfigFinanciera> listarConfiguraciones() {
        return configRepo.findAllByOrderByEmpIdAsc();
    }

    // ── Métodos internos de cálculo de liquidación ──────────────────────────

    private LiquidacionPagoDto calcularLiquidacionEmpleado(Empleado emp, LocalDate desde, LocalDate hasta) {
        LiquidacionPagoDto dto = new LiquidacionPagoDto();

        dto.setEmpId(emp.getEmpId());
        dto.setNombreCompleto(emp.getNombreCompleto());
        dto.setDepartamento(emp.getDepartamentoNombre());
        dto.setPeriodoInicio(desde.toString());
        dto.setPeriodoFin(hasta.toString());

        // Obtener configuración financiera
        EmpleadoConfigFinanciera conf = obtenerOConfigurarFinanciera(emp.getEmpId());
        boolean practicante = esPracticante(emp);
        String cargoEmp = (emp.getCargo() != null && !emp.getCargo().isEmpty()) ? emp.getCargo() : (practicante ? "Practicante" : "Personal");
        dto.setCargo((conf.getCargo() != null && !conf.getCargo().equals("Personal")) ? conf.getCargo() : cargoEmp);

        BigDecimal sueldo = practicante ? BigDecimal.ZERO : conf.getSueldoMensual();
        dto.setSueldoMensual(sueldo);
        dto.setValorDia(practicante ? BigDecimal.ZERO : conf.getValorDia().setScale(2, RoundingMode.HALF_UP));
        dto.setValorHora(practicante ? BigDecimal.ZERO : conf.getValorHora().setScale(2, RoundingMode.HALF_UP));
        dto.setValorMinuto(practicante ? BigDecimal.ZERO : conf.getValorMinuto().setScale(4, RoundingMode.HALF_UP));
        dto.setExcedenteCompensaDeficit(conf.getExcedenteCompensaDeficit());

        // Días hábiles (Lunes a Viernes)
        List<LocalDate> diasHabiles = new ArrayList<>();
        LocalDate cur = desde;
        while (!cur.isAfter(hasta)) {
            if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                diasHabiles.add(cur);
            }
            cur = cur.plusDays(1);
        }
        dto.setDiasEvaluados(diasHabiles.size());

        // Obtener registros de asistencia del empleado en el rango
        List<RegistroAsistencia> registros = registroRepo.findByEmpIdAndFechaBetweenOrderByFechaAsc(emp.getEmpId(), desde, hasta);
        dto.setDiasConRegistro(registros.size());

        // Mapear registros por fecha
        Map<LocalDate, RegistroAsistencia> regPorFecha = registros.stream()
                .collect(Collectors.toMap(RegistroAsistencia::getFecha, r -> r, (r1, r2) -> r1));

        // Ausentismo real (Días sin ninguna marcación en día hábil)
        int diasFalta = 0;
        for (LocalDate dia : diasHabiles) {
            RegistroAsistencia r = regPorFecha.get(dia);
            if (r == null || (r.getNumMarcaciones() != null && r.getNumMarcaciones() == 0)) {
                diasFalta++;
            }
        }
        dto.setDiasFalta(diasFalta);

        // Sumatorias de asistencia y auditoría de justificaciones
        long totalMinTardanza = 0;
        long minJustificados = 0;
        long minNoJustificados = 0;
        double sumHorasEfectivas = 0.0;
        double sumHorasExcedente = 0.0;
        double sumHorasDeficit = 0.0;
        int cantidadOmisiones = 0;

        List<LiquidacionPagoDto.JustificacionResumenDto> justificacionesResumen = new ArrayList<>();

        for (RegistroAsistencia r : registros) {
            boolean esSabado = r.getFecha() != null && r.getFecha().getDayOfWeek() == DayOfWeek.SATURDAY;
            if (!esSabado && r.getHorasEfectivasDecimal() != null) {
                sumHorasEfectivas += r.getHorasEfectivasDecimal();
            }

            // REGLA ESTRICTA DE TOLERANCIA Y PROTECCIÓN DE SUBVENCIÓN:
            // Tolerancia de 10 minutos exactos al inicio de la jornada (límite 08:10:00).
            // Si el trabajador marca su entrada entre las 08:00:00 y las 08:10:00 (o antes), no afecta al salario (0 min).
            // Si marca después de las 08:10:00, solo se computan los minutos excedentes a partir de las 08:10:00.
            LocalTime entrada = r.getHoraEntrada();
            boolean dentroDeTolerancia = entrada == null || !entrada.isAfter(LocalTime.of(8, 10, 0));
            Long tardActual = 0L;
            if (!dentroDeTolerancia) {
                long segs = ChronoUnit.SECONDS.between(LocalTime.of(8, 10, 0), entrada);
                tardActual = Math.max(0L, segs / 60);
            }
            Long tardOrig = tardActual;
            totalMinTardanza += tardOrig;

            boolean estaJustificado = r.getEstadoJustificacion() != null &&
                    !r.getEstadoJustificacion().equalsIgnoreCase("NINGUNO") &&
                    !r.getEstadoJustificacion().equalsIgnoreCase("APLICAR_DESCUENTO");

            if (estaJustificado) {
                minJustificados += (tardOrig - tardActual);
                minNoJustificados += tardActual;

                // Agregar al detalle de justificaciones
                LiquidacionPagoDto.JustificacionResumenDto jDto = new LiquidacionPagoDto.JustificacionResumenDto();
                jDto.setRegistroId(r.getId());
                jDto.setFecha(r.getFecha() != null ? r.getFecha().toString() : "");
                jDto.setTipoJustificacion(r.getEstadoJustificacion());
                jDto.setObservacion(r.getObservacionDoctor());
                jDto.setMinutosExonerados(tardOrig - tardActual);
                jDto.setHorasExoneradas(r.getBalanceJornadaOriginal() != null && r.getBalanceJornadaOriginal() < 0 ? Math.abs(r.getBalanceJornadaOriginal()) : 0.0);
                jDto.setAplicadoPor("DOCTOR");
                jDto.setCreadoEn(r.getActualizadoEn() != null ? r.getActualizadoEn().toString() : "");
                justificacionesResumen.add(jDto);
            } else {
                minNoJustificados += tardActual;
            }

            // Balance de jornada (Excedente vs Déficit) - Excluir sábados de sumatorias
            if (!esSabado && r.getBalanceJornada() != null) {
                if (r.getBalanceJornada() > 0) {
                    sumHorasExcedente += r.getBalanceJornada();
                } else if (r.getBalanceJornada() < 0) {
                    sumHorasDeficit += Math.abs(r.getBalanceJornada());
                }
            }

            // Omisión de marcado (solo aplica a días hábiles Lunes a Viernes)
            String cond = r.getCondicionIngreso() != null ? r.getCondicionIngreso() : "";
            if (!esSabado && (cond.contains("Omisión") || cond.contains("Sin Marcación")) && !estaJustificado) {
                cantidadOmisiones++;
            }
        }

        dto.setTotalMinutosTardanza(totalMinTardanza);
        dto.setMinutosJustificados(minJustificados);
        dto.setMinutosTardanzaNetosNoJustificados(minNoJustificados);
        dto.setTotalHorasEfectivas(redondearDbl(sumHorasEfectivas));
        dto.setTotalHorasExcedente(redondearDbl(sumHorasExcedente));
        dto.setTotalHorasDeficit(redondearDbl(sumHorasDeficit));
        dto.setCantidadOmisionMarcado(cantidadOmisiones);
        dto.setJustificaciones(justificacionesResumen);

        // Regla de compensación de Horas Extra vs Déficit
        double horasDeficitNetas = sumHorasDeficit;
        BigDecimal bonificacionHorasExtra = BigDecimal.ZERO;

        if (Boolean.TRUE.equals(conf.getExcedenteCompensaDeficit())) {
            if (sumHorasExcedente >= sumHorasDeficit) {
                horasDeficitNetas = 0.0;
            } else {
                horasDeficitNetas = sumHorasDeficit - sumHorasExcedente;
            }
        } else {
            // No compensa: el déficit se descuenta completo y el excedente se paga como bonificación
            bonificacionHorasExtra = BigDecimal.valueOf(sumHorasExcedente)
                    .multiply(conf.getValorHora())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        dto.setHorasDeficitNetas(redondearDbl(horasDeficitNetas));
        dto.setBonificacionHorasExtra(bonificacionHorasExtra);

        // Practicantes no perciben salario ni se les aplican deducciones salariales
        if (practicante || sueldo.compareTo(BigDecimal.ZERO) == 0) {
            dto.setDescuentoTardanzas(BigDecimal.ZERO);
            dto.setDescuentoDeficitHoras(BigDecimal.ZERO);
            dto.setDescuentoFaltas(BigDecimal.ZERO);
            dto.setPenalidadOmisionMarcado(BigDecimal.ZERO);
            dto.setTotalDescuentos(BigDecimal.ZERO);
            dto.setNetoAPagar(BigDecimal.ZERO);
            dto.setBonificacionHorasExtra(BigDecimal.ZERO);
            return dto;
        }

        // Cálculos financieros de descuentos
        BigDecimal descTardanza = BigDecimal.valueOf(minNoJustificados)
                .multiply(conf.getValorMinuto())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal descDeficit = BigDecimal.valueOf(horasDeficitNetas)
                .multiply(conf.getValorHora())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal descFaltas = BigDecimal.valueOf(diasFalta)
                .multiply(conf.getValorDia())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal penalidadOmision = BigDecimal.ZERO;
        if (cantidadOmisiones > 0) {
            if (conf.getMultaOmision().compareTo(BigDecimal.ZERO) > 0) {
                penalidadOmision = conf.getMultaOmision()
                        .multiply(BigDecimal.valueOf(cantidadOmisiones))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                // Si la multa configurada es 0, penaliza con medio día de descuento por omisión
                penalidadOmision = conf.getValorDia()
                        .multiply(new BigDecimal("0.5"))
                        .multiply(BigDecimal.valueOf(cantidadOmisiones))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal totalDescuentos = descTardanza.add(descDeficit).add(descFaltas).add(penalidadOmision);
        BigDecimal netoAPagar = conf.getSueldoMensual()
                .subtract(totalDescuentos)
                .add(bonificacionHorasExtra);

        if (netoAPagar.compareTo(BigDecimal.ZERO) < 0) {
            netoAPagar = BigDecimal.ZERO;
        }

        dto.setDescuentoTardanzas(descTardanza);
        dto.setDescuentoDeficitHoras(descDeficit);
        dto.setDescuentoFaltas(descFaltas);
        dto.setPenalidadOmisionMarcado(penalidadOmision);
        dto.setTotalDescuentos(totalDescuentos);
        dto.setNetoAPagar(netoAPagar.setScale(2, RoundingMode.HALF_UP));

        return dto;
    }

    private List<Empleado> obtenerEmpleados(String departamento, String empId) {
        if (empId != null && !empId.trim().isEmpty()) {
            return empleadoRepo.findById(empId.trim())
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
        }
        if (departamento != null && !departamento.trim().isEmpty()) {
            Set<String> descendientes = departamentoRepo.obtenerNombresDescendientesDe(departamento.trim());
            return empleadoRepo.findByActivoTrueAndDepartamentoIn(descendientes);
        }
        List<Empleado> lista = empleadoRepo.findByActivoTrueOrderByDepartamentoAscApellidosAsc();
        if (lista.isEmpty()) {
            // Sincronizar desde registro_asistencia si empleado está vacío
            List<RegistroAsistencia> todos = registroRepo.findAll();
            Map<String, Empleado> deducidos = new LinkedHashMap<>();
            for (RegistroAsistencia r : todos) {
                if (r.getEmpId() != null && !deducidos.containsKey(r.getEmpId())) {
                    deducidos.put(r.getEmpId(), new Empleado(
                            r.getEmpId(),
                            r.getNombres() != null ? r.getNombres() : "",
                            r.getApellidos() != null ? r.getApellidos() : "",
                            r.getEmpresa() != null ? r.getEmpresa() : "PRORRIDRE",
                            r.getDepartamento() != null ? r.getDepartamento() : "SIN DEPARTAMENTO"
                    ));
                }
            }
            if (!deducidos.isEmpty()) {
                lista = new ArrayList<>(deducidos.values());
                empleadoRepo.saveAll(lista);
            }
        }
        return lista;
    }

    private double redondearDbl(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

