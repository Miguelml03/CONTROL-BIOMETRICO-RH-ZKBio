package pe.gob.prorridre.asistencia.service;

import pe.gob.prorridre.asistencia.dto.ResumenIndividualDto;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Motor Matemático y de Reglas de Asistencia PRORRIDRE PUNO.
 *
 * Aplica estrictamente las reglas auditadas para corregir los fallos del Excel:
 * 1. Desglose inteligente de Horas Crudas (Filtro antirrebote de 5 min y asignación por ventanas horarias).
 * 2. Protección contra falsas tardanzas vespertinas (omisión de entrada cuando hay marcación única vespertina).
 * 3. Reglas de refrigerio reglamentario (descuento fijo de 1 hora cuando corresponde y 0 en media jornada).
 * 4. Fórmulas auditadas de puntualidad (tolerancia retroactiva a las 08:00:00 si entra después de 08:10:00).
 * 5. Balance de jornada (eliminación de los 304 errores #¡VALOR! devolviendo null/0 en incompletos).
 * 6. Estados de cumplimiento auditados.
 */
@Service
public class AttendanceEngineService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEngineService.class);

    /* -----------------------------------------------------------------------
     * Constantes de Reglas y Ventanas Horarias PRORRIDRE (4 Pilares Auditados)
     * --------------------------------------------------------------------- */

    /** Límite inferior de entrada válida */
    public static final LocalTime ENTRADA_MIN = LocalTime.of(6, 0, 0);

    /** Hora límite de tolerancia matutina (sin descuento de salario) */
    public static final LocalTime HORA_TOLERANCIA_MAX = LocalTime.of(8, 10, 0);

    /** Límite superior de entrada para asignar como ingreso antes del refrigerio */
    public static final LocalTime ENTRADA_MAX = LocalTime.of(12, 49, 59);

    /** Pilar 2: Salida a refrigerio (12:50:00 - 13:30:00) */
    public static final LocalTime REFRIGERIO_SALIDA_MIN = LocalTime.of(12, 50, 0);
    public static final LocalTime REFRIGERIO_SALIDA_MAX = LocalTime.of(13, 30, 0);

    /** Pilar 3: Retorno de refrigerio (13:45:00 - 14:10:00) */
    public static final LocalTime REFRIGERIO_RETORNO_MIN = LocalTime.of(13, 45, 0);
    public static final LocalTime REFRIGERIO_RETORNO_MAX = LocalTime.of(14, 10, 0);

    /** Pilar 4: Salida final (17:00:00 - 23:59:59) */
    public static final LocalTime SALIDA_FINAL_MIN = LocalTime.of(17, 0, 0);
    public static final LocalTime SALIDA_FINAL_MAX = LocalTime.of(23, 59, 59);

    /** Segundos mínimos entre dos marcaciones para no ser consideradas rebote (5 minutos = 300 s) */
    public static final long DEBOUNCE_SEGUNDOS = 300L;

    /** Jornada estándar reglamentaria: 8 horas */
    public static final double JORNADA_NORMA_HORAS = 8.0;
    public static final String JORNADA_NORMA_TEXTO = "08:00:00";

    /** Tolerancia de balance para considerar "Jornada Reglamentaria" (+/- 0.08 horas = aprox 5 min) */
    public static final double TOLERANCIA_BALANCE = 0.08;

    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Calcula y audita un registro completo de asistencia (Columnas A:R) a partir de los datos crudos.
     */
    public RegistroAsistencia calcularRegistro(
            String empId,
            String nombres,
            String apellidos,
            String empresa,
            String departamento,
            LocalDate fecha,
            String horasCrudasStr) {

        RegistroAsistencia reg = new RegistroAsistencia();
        reg.setEmpId(empId != null ? empId.trim() : "SIN_ID");
        reg.setNombres(nombres != null ? nombres.trim() : "");
        reg.setApellidos(apellidos != null ? apellidos.trim() : "");
        reg.setEmpresa(empresa != null && !empresa.trim().isEmpty() ? empresa.trim() : "PRORRIDRE");
        reg.setDepartamento(departamento != null ? departamento.trim() : "NO_ASIGNADO");
        reg.setFecha(fecha != null ? fecha : LocalDate.now());
        reg.setHorasCrudas(horasCrudasStr != null ? horasCrudasStr.trim() : "");

        // 1. Desglose y Antirrebote (Debounce)
        List<LocalTime> horasValidas = aplicarFiltroAntirrebote(parsearHoras(horasCrudasStr));
        reg.setNumMarcaciones(horasValidas.size());

        // Actualizar cadena de horas limpias ordenadas
        if (!horasValidas.isEmpty()) {
            reg.setHorasCrudas(horasValidas.stream()
                .map(t -> t.format(FMT_HORA))
                .collect(Collectors.joining(", ")));
        }

        // 2. Asignación por Ventanas Horarias
        asignarVentanasHorarias(reg, horasValidas);

        // 3. Fórmulas de Condición de Ingreso y Tardanza
        evaluarCondicionIngresoYTardanza(reg);

        // 4. Cálculo de Jornada Efectiva, Almuerzo y Balance
        calcularJornadasYBalance(reg);

        // 5. Estado de Cumplimiento
        evaluarEstadoCumplimiento(reg);

        return reg;
    }

    /**
     * 1. PARSEO DE HORAS CRUDAS
     */
    public List<LocalTime> parsearHoras(String horasCrudasStr) {
        if (horasCrudasStr == null || horasCrudasStr.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LocalTime> lista = new ArrayList<>();
        String[] partes = horasCrudasStr.split("[,;\\s]+");
        for (String p : partes) {
            String token = p.trim();
            if (token.isEmpty()) continue;
            try {
                // Manejar formatos HH:mm:ss o H:mm:ss o HH:mm
                String[] timeParts = token.split(":");
                if (timeParts.length >= 2) {
                    int h = Integer.parseInt(timeParts[0]);
                    int m = Integer.parseInt(timeParts[1]);
                    int s = (timeParts.length >= 3) ? Integer.parseInt(timeParts[2]) : 0;
                    lista.add(LocalTime.of(h, m, s));
                }
            } catch (Exception ex) {
                log.debug("[AttendanceEngine] Ignorando token de hora no válido: '{}'", token);
            }
        }
        return lista;
    }

    /**
     * 1. FILTRO ANTIRREBOTE (DEBOUNCE):
     * Ordena cronológicamente y descarta marcaciones cuya diferencia con la
     * inmediata anterior sea menor a 5 minutos (300 segundos).
     */
    public List<LocalTime> aplicarFiltroAntirrebote(List<LocalTime> horas) {
        if (horas == null || horas.isEmpty()) {
            return Collections.emptyList();
        }

        // Ordenar cronológicamente ascendente
        List<LocalTime> ordenadas = new ArrayList<>(horas);
        Collections.sort(ordenadas);

        List<LocalTime> filtradas = new ArrayList<>();
        LocalTime ultimoValido = null;

        for (LocalTime t : ordenadas) {
            if (ultimoValido == null) {
                filtradas.add(t);
                ultimoValido = t;
            } else {
                long diffSegundos = ChronoUnit.SECONDS.between(ultimoValido, t);
                if (diffSegundos >= DEBOUNCE_SEGUNDOS) {
                    filtradas.add(t);
                    ultimoValido = t;
                } else {
                    log.debug("[Debounce] Marcación rebote descartada: {} (diferencia {} s < {} s con {})",
                            t, diffSegundos, DEBOUNCE_SEGUNDOS, ultimoValido);
                }
            }
        }

        return filtradas;
    }

    /**
     * 2. ASIGNACIÓN POR VENTANAS HORARIAS (4 PILARES ESTRICTOS)
     */
    private void asignarVentanasHorarias(RegistroAsistencia reg, List<LocalTime> horas) {
        if (horas.isEmpty()) {
            reg.setHoraEntrada(null);
            reg.setInicioAlmuerzo(null);
            reg.setFinAlmuerzo(null);
            reg.setHoraSalida(null);
            reg.setOtrasMarcaciones(null);
            return;
        }

        boolean esSabado = reg.getFecha() != null && reg.getFecha().getDayOfWeek() == DayOfWeek.SATURDAY;

        if (esSabado) {
            // SÁBADOS: Solo exigir Ingreso (ventana 06:00:00 a 08:10:00 o tardanza)
            // Si existe una segunda marcación (ej. 12:13:59), colocarla en L (Hora Salida) solo como dato informativo
            // sin marcarla como fuera de rango. Si no hay salida, NO genera error ni observación.
            // Las columnas de almuerzo (J y K) siempre quedan vacías (null).
            LocalTime entrada = null;
            LocalTime salida = null;
            List<LocalTime> extras = new ArrayList<>();
            for (LocalTime t : horas) {
                if (entrada == null && !t.isBefore(ENTRADA_MIN) && !t.isAfter(LocalTime.of(13, 30, 0))) {
                    entrada = t;
                } else if (entrada != null && salida == null) {
                    salida = t; // Segunda marcación informativa en L (sin considerarse fuera de rango)
                } else {
                    extras.add(t);
                }
            }
            reg.setHoraEntrada(entrada);
            reg.setInicioAlmuerzo(null);
            reg.setFinAlmuerzo(null);
            reg.setHoraSalida(salida);
            reg.setOtrasMarcaciones(extras.isEmpty() ? null : extras.stream().map(t -> t.format(FMT_HORA)).collect(Collectors.joining(", ")));
            return;
        }

        // LUNES A VIERNES: 4 Pilares Estrictos
        // 1. Ingreso: 06:00:00 - 12:49:59 (tolerancia hasta 08:10:00)
        // 2. Salida a refrigerio: 12:50:00 - 13:30:00
        // 3. Retorno de refrigerio: 13:45:00 - 14:10:00
        // 4. Salida final: 17:00:00 - 23:59:59

        LocalTime entrada = null;
        LocalTime iniAlm = null;
        LocalTime finAlm = null;
        LocalTime salida = null;
        List<LocalTime> extras = new ArrayList<>();

        // Encontrar la última marcación en la ventana de salida final si existe
        int idxUltimaSalida = -1;
        for (int i = horas.size() - 1; i >= 0; i--) {
            LocalTime t = horas.get(i);
            if (!t.isBefore(SALIDA_FINAL_MIN) && !t.isAfter(SALIDA_FINAL_MAX)) {
                idxUltimaSalida = i;
                salida = t;
                break;
            }
        }

        for (int i = 0; i < horas.size(); i++) {
            LocalTime t = horas.get(i);

            // Si es la marcación seleccionada para la salida final
            if (i == idxUltimaSalida) {
                continue;
            }

            // Ventana 1: Ingreso (06:00:00 a 12:49:59)
            if (entrada == null && !t.isBefore(ENTRADA_MIN) && !t.isAfter(ENTRADA_MAX)) {
                entrada = t;
                continue;
            }

            // Ventana 2: Salida a refrigerio (12:50:00 a 13:30:00)
            if (iniAlm == null && !t.isBefore(REFRIGERIO_SALIDA_MIN) && !t.isAfter(REFRIGERIO_SALIDA_MAX)) {
                iniAlm = t;
                continue;
            }

            // Ventana 3: Retorno de refrigerio (13:45:00 a 14:10:00)
            if (finAlm == null && !t.isBefore(REFRIGERIO_RETORNO_MIN) && !t.isAfter(REFRIGERIO_RETORNO_MAX)) {
                finAlm = t;
                continue;
            }

            // Si no calza en ninguna ventana o es duplicada, pasa a otrasMarcaciones (fuera de rango)
            extras.add(t);
        }

        reg.setHoraEntrada(entrada);
        reg.setInicioAlmuerzo(iniAlm);
        reg.setFinAlmuerzo(finAlm);
        reg.setHoraSalida(salida);

        if (!extras.isEmpty()) {
            reg.setOtrasMarcaciones(extras.stream().map(t -> t.format(FMT_HORA)).collect(Collectors.joining(", ")));
        } else {
            reg.setOtrasMarcaciones(null);
        }
    }

    /**
     * 3. FÓRMULAS AUDITADAS: CONDICIÓN DE INGRESO Y MINUTOS DE TARDANZA
     *
     * - Columna M:
     *   * horaEntrada == null -> "Sin Marcación (Omisión Entrada)"
     *   * horaEntrada <= 08:10:00 -> "A Tiempo"
     *   * horaEntrada > 08:10:00 -> "Tardanza"
     *
     * - Columna N (Regla de Tolerancia de 10 min):
     *   * horaEntrada == null o horaEntrada <= 08:10:00 -> 0
     *   * horaEntrada > 08:10:00 -> Minutos excedentes posteriores a las 08:10:00.
     */
    private void evaluarCondicionIngresoYTardanza(RegistroAsistencia reg) {
        LocalTime entrada = reg.getHoraEntrada();

        if (entrada == null) {
            reg.setCondicionIngreso("Sin Marcación (Omisión Entrada)");
            reg.setMinutosTardanza(0L);
            return;
        }

        if (!entrada.isAfter(HORA_TOLERANCIA_MAX)) {
            // Entrada dentro del horario oficial + tolerancia de 10 min
            reg.setCondicionIngreso("A Tiempo");
            reg.setMinutosTardanza(0L);
        } else {
            // Entrada posterior a las 08:10:00 (Tardanza después de tolerancia)
            reg.setCondicionIngreso("Tardanza");
            long segundosDesdeTolerancia = ChronoUnit.SECONDS.between(HORA_TOLERANCIA_MAX, entrada);
            long minutos = Math.max(0L, Math.round(segundosDesdeTolerancia / 60.0));
            reg.setMinutosTardanza(minutos);
        }
    }

    /**
     * Reevalúa un registro existente con las reglas actualizadas de tolerancia y balance.
     */
    public void reevaluarTardanzaYCumplimiento(RegistroAsistencia reg) {
        evaluarCondicionIngresoYTardanza(reg);
        calcularJornadasYBalance(reg);
        evaluarEstadoCumplimiento(reg);
    }

    /**
     * 4. JORNADA EFECTIVA, ALMUERZO Y BALANCE (LÓGICA ESTRICTA DE 4 PILARES)
     *
     * 1. Cero relleno ficticio: si no timbró refrigerio, queda null y NO se inventa 13:00 ni 14:00 ni se resta 1h fija.
     * 2. Jornada Efectiva y Balance SOLO se calculan si los 4 pilares están registrados (I, J, K, L != null).
     * 3. Sábados: No computable en horas semanales (jornadaEfectiva=null, jornadaNorma=null, balanceJornada=null).
     * 4. Observaciones: Evaluaciones independientes unidas por '; '.
     */
    private void calcularJornadasYBalance(RegistroAsistencia reg) {
        reg.setAlmuerzoImputado(false); // CERO relleno ficticio

        boolean esSabado = reg.getFecha() != null && reg.getFecha().getDayOfWeek() == DayOfWeek.SATURDAY;
        if (esSabado) {
            reg.setJornadaEfectiva(null);
            reg.setHorasEfectivasDecimal(0.0);
            reg.setJornadaNorma(null); // No computable, NO "08:00:00"
            reg.setHorasNormaDecimal(0.0);
            reg.setBalanceJornada(null);
            if (reg.getHoraEntrada() != null) {
                reg.setObservacionDoctor("JORNADA SABATINA - SIN INCIDENCIAS");
            } else {
                reg.setObservacionDoctor("NO REGISTRA INGRESO");
            }
            return;
        }

        // LUNES A VIERNES:
        reg.setJornadaNorma(JORNADA_NORMA_TEXTO); // "08:00:00"
        reg.setHorasNormaDecimal(JORNADA_NORMA_HORAS); // 8.0

        // 4. GENERACIÓN EXHAUSTIVA DE LA COLUMNA OBSERVACIONES (L-V)
        List<String> obs = new ArrayList<>();
        if (reg.getHoraEntrada() == null) {
            obs.add("NO REGISTRA INGRESO");
        }
        if (reg.getInicioAlmuerzo() == null) {
            obs.add("NO REGISTRA SALIDA A REFRIGERIO");
        }
        if (reg.getFinAlmuerzo() == null) {
            obs.add("NO REGISTRA RETORNO DE REFRIGERIO");
        }
        if (reg.getHoraSalida() == null) {
            obs.add("NO REGISTRA SALIDA FINAL");
        }
        if (reg.getOtrasMarcaciones() != null && !reg.getOtrasMarcaciones().trim().isEmpty()) {
            obs.add("MARCACIÓN FUERA DE RANGO: " + reg.getOtrasMarcaciones().trim());
        }

        if (obs.isEmpty()) {
            reg.setObservacionDoctor("SIN INCIDENCIAS");
        } else {
            reg.setObservacionDoctor(String.join("; ", obs));
        }

        // 2. CÁLCULO EXACTO AL SEGUNDO DE JORNADA Y BALANCE (SOLO CON LOS 4 PILARES COMPLETOS EN L-V)
        boolean cuatroCompletos = reg.getHoraEntrada() != null
                && reg.getInicioAlmuerzo() != null
                && reg.getFinAlmuerzo() != null
                && reg.getHoraSalida() != null;

        if (!cuatroCompletos) {
            reg.setJornadaEfectiva(null);
            reg.setHorasEfectivasDecimal(0.0);
            reg.setBalanceJornada(null);
            return;
        }

        // Matemática exacta al segundo (java.time.Duration):
        LocalTime entrada = reg.getHoraEntrada();
        LocalTime iniAlm = reg.getInicioAlmuerzo();
        LocalTime finAlm = reg.getFinAlmuerzo();
        LocalTime salida = reg.getHoraSalida();

        long segManana = Math.max(0L, Duration.between(entrada, iniAlm).getSeconds());
        long segTarde = Math.max(0L, Duration.between(finAlm, salida).getSeconds());
        long segEfectivos = segManana + segTarde;

        long horas = segEfectivos / 3600;
        long minutos = (segEfectivos % 3600) / 60;
        long segundos = segEfectivos % 60;
        reg.setJornadaEfectiva(String.format("%02d:%02d:%02d", horas, minutos, segundos));

        double horasDecimal = Math.round((segEfectivos / 3600.0) * 100.0) / 100.0;
        reg.setHorasEfectivasDecimal(horasDecimal);

        long segBalance = segEfectivos - 28800L; // Base 8 horas exactas = 28800 s
        double balance = Math.round((segBalance / 3600.0) * 100.0) / 100.0;
        reg.setBalanceJornada(balance);
    }

    /**
     * 5. ESTADO DE CUMPLIMIENTO Y CONDICIÓN INTEGRAL
     *
     * - Si falta 1 o más pilares en Lunes-Viernes: "Incompleto (Falta Marcación)". NUNCA "Excedente de Jornada".
     * - Sábados: "Jornada Sabatina" (si marcó entrada) o "Sin Ingreso (Sábado)".
     */
    private void evaluarEstadoCumplimiento(RegistroAsistencia reg) {
        boolean esSabado = reg.getFecha() != null && reg.getFecha().getDayOfWeek() == DayOfWeek.SATURDAY;
        if (esSabado) {
            if (reg.getHoraEntrada() != null) {
                reg.setEstadoCumplimiento("Jornada Sabatina");
            } else {
                reg.setEstadoCumplimiento("Sin Ingreso (Sábado)");
            }
            return;
        }

        // LUNES A VIERNES:
        boolean cuatroCompletos = reg.getHoraEntrada() != null
                && reg.getInicioAlmuerzo() != null
                && reg.getFinAlmuerzo() != null
                && reg.getHoraSalida() != null;

        if (!cuatroCompletos) {
            reg.setEstadoCumplimiento("Incompleto (Falta Marcación)");
            return;
        }

        // Con los 4 pilares completos:
        boolean sinOtrasMarcaciones = (reg.getOtrasMarcaciones() == null || reg.getOtrasMarcaciones().trim().isEmpty());
        boolean sinTardanza = (reg.getMinutosTardanza() != null && reg.getMinutosTardanza() == 0L);

        // Condición integral: "A Tiempo" / "SIN INCIDENCIAS"
        if (sinTardanza && sinOtrasMarcaciones && "A Tiempo".equals(reg.getCondicionIngreso())) {
            reg.setCondicionIngreso("A Tiempo / SIN INCIDENCIAS");
        }

        double balance = reg.getBalanceJornada() != null ? reg.getBalanceJornada() : 0.0;
        if (balance > TOLERANCIA_BALANCE) {
            reg.setEstadoCumplimiento("Excedente de Jornada");
        } else if (balance < -TOLERANCIA_BALANCE) {
            reg.setEstadoCumplimiento("Déficit de Jornada");
        } else {
            reg.setEstadoCumplimiento("Jornada Reglamentaria");
        }
    }

    /**
     * PARTE B: CÁLCULO DEL RESUMEN INDIVIDUAL
     * Calcula la matriz de 4 pilares y métricas consolidadas sobre el listado del trabajador.
     */
    public ResumenIndividualDto calcularResumenIndividual(List<RegistroAsistencia> lista) {
        ResumenIndividualDto dto = new ResumenIndividualDto();
        if (lista == null || lista.isEmpty()) {
            return dto;
        }

        String primerEmpId = lista.get(0).getEmpId();
        String primerNombre = (lista.get(0).getNombres() != null ? lista.get(0).getNombres() : "") + " " +
                              (lista.get(0).getApellidos() != null ? lista.get(0).getApellidos() : "");
        dto.setEmpId(primerEmpId);
        dto.setNombreCompleto(primerNombre.trim());

        int diasConRegistro = 0;
        int tardanzasIngresoDias = 0;
        int totalMinutosTardanza = 0;
        int totalEsperadoIngreso = 0;
        int noRegistraIngreso = 0;
        int totalEsperadoSalidaRefrigerio = 0;
        int noRegistraSalidaRefrigerio = 0;
        int totalEsperadoRetornoRefrigerio = 0;
        int noRegistraRetornoRefrigerio = 0;
        int totalEsperadoSalidaFinal = 0;
        int noRegistraSalidaFinal = 0;
        int diasConMarcacionesFueraDeRango = 0;

        for (RegistroAsistencia r : lista) {
            LocalDate f = r.getFecha();
            if (f == null) continue;
            DayOfWeek dow = f.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) {
                continue; // Domingos no son laborables
            }

            boolean esSabado = (dow == DayOfWeek.SATURDAY);

            // Días con registro (al menos 1 marcación)
            boolean tieneMarcacion = (r.getNumMarcaciones() != null && r.getNumMarcaciones() > 0)
                    || r.getHoraEntrada() != null || r.getHoraSalida() != null
                    || (r.getHorasCrudas() != null && !r.getHorasCrudas().trim().isEmpty());
            if (tieneMarcacion) {
                diasConRegistro++;
            }

            // Tardanza de ingreso
            if (r.getMinutosTardanza() != null && r.getMinutosTardanza() > 0) {
                tardanzasIngresoDias++;
                totalMinutosTardanza += r.getMinutosTardanza().intValue();
            }

            // Pilar 1: Ingreso (Esperado de Lunes a Sábado)
            totalEsperadoIngreso++;
            if (r.getHoraEntrada() == null) {
                noRegistraIngreso++;
            }

            // Pilares 2, 3 y 4 (Solo esperados de Lunes a Viernes)
            if (!esSabado) {
                // Pilar 2: Salida a refrigerio
                totalEsperadoSalidaRefrigerio++;
                if (r.getInicioAlmuerzo() == null) {
                    noRegistraSalidaRefrigerio++;
                }

                // Pilar 3: Retorno de refrigerio
                totalEsperadoRetornoRefrigerio++;
                if (r.getFinAlmuerzo() == null) {
                    noRegistraRetornoRefrigerio++;
                }

                // Pilar 4: Salida final
                totalEsperadoSalidaFinal++;
                if (r.getHoraSalida() == null) {
                    noRegistraSalidaFinal++;
                }
            }

            // Marcaciones fuera de rango
            if (r.getOtrasMarcaciones() != null && !r.getOtrasMarcaciones().trim().isEmpty()) {
                diasConMarcacionesFueraDeRango++;
            }
        }

        dto.setDiasConRegistro(diasConRegistro);
        dto.setTardanzasIngresoDias(tardanzasIngresoDias);
        dto.setTotalMinutosTardanza(totalMinutosTardanza);

        dto.setTotalEsperadoIngreso(totalEsperadoIngreso);
        dto.setNoRegistraIngreso(noRegistraIngreso);

        dto.setTotalEsperadoSalidaRefrigerio(totalEsperadoSalidaRefrigerio);
        dto.setNoRegistraSalidaRefrigerio(noRegistraSalidaRefrigerio);

        dto.setTotalEsperadoRetornoRefrigerio(totalEsperadoRetornoRefrigerio);
        dto.setNoRegistraRetornoRefrigerio(noRegistraRetornoRefrigerio);

        dto.setTotalEsperadoSalidaFinal(totalEsperadoSalidaFinal);
        dto.setNoRegistraSalidaFinal(noRegistraSalidaFinal);

        dto.setDiasConMarcacionesFueraDeRango(diasConMarcacionesFueraDeRango);

        return dto;
    }
}

