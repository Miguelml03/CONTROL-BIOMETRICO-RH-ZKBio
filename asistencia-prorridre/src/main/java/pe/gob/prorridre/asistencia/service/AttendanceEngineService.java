package pe.gob.prorridre.asistencia.service;

import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
     * Constantes de Reglas y Ventanas Horarias PRORRIDRE
     * --------------------------------------------------------------------- */

    /** Límite inferior de entrada válida */
    public static final LocalTime ENTRADA_MIN = LocalTime.of(5, 0, 0);

    /** Límite superior de entrada válida */
    public static final LocalTime ENTRADA_MAX = LocalTime.of(11, 30, 0);

    /** Hora oficial de inicio de jornada */
    public static final LocalTime HORA_INICIO_OFICIAL = LocalTime.of(8, 0, 0);

    /** Hora límite de tolerancia matutina */
    public static final LocalTime HORA_TOLERANCIA_MAX = LocalTime.of(8, 10, 0);

    /** Límite inferior de inicio de almuerzo */
    public static final LocalTime ALMUERZO_INICIO_MIN = LocalTime.of(12, 0, 0);

    /** Límite superior de inicio de almuerzo */
    public static final LocalTime ALMUERZO_INICIO_MAX = LocalTime.of(13, 35, 0);

    /** Límite inferior de fin de almuerzo */
    public static final LocalTime ALMUERZO_FIN_MIN = LocalTime.of(13, 35, 1);

    /** Límite superior de fin de almuerzo */
    public static final LocalTime ALMUERZO_FIN_MAX = LocalTime.of(15, 30, 0);

    /** Límite inferior de salida vespertina habitual */
    public static final LocalTime SALIDA_TARDE_MIN = LocalTime.of(15, 30, 1);

    /** Hora umbral para considerar media jornada matutina (sin descuento de almuerzo) */
    public static final LocalTime UMBRAL_MEDIA_JORNADA = LocalTime.of(14, 0, 0);

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
     * 2. ASIGNACIÓN POR VENTANAS HORARIAS (TIME BUCKETS)
     */
    private void asignarVentanasHorarias(RegistroAsistencia reg, List<LocalTime> horas) {
        if (horas.isEmpty()) {
            reg.setHoraEntrada(null);
            reg.setInicioAlmuerzo(null);
            reg.setFinAlmuerzo(null);
            reg.setHoraSalida(null);
            return;
        }

        // REGLA CRÍTICA CONTRA FALSAS TARDANZAS VESPERTINAS:
        // Si hay una sola marcación en el día y ocurrió después de las 12:00:00 (ej. 20:29:19 o 18:28:10),
        // NUNCA asignarla a horaEntrada. Se asigna a salida o almuerzo según ventana,
        // dejando horaEntrada = null y minutosTardanza = 0 ("Sin Marcación (Omisión Entrada)").
        if (horas.size() == 1) {
            LocalTime unica = horas.get(0);
            if (!unica.isBefore(ALMUERZO_INICIO_MIN)) {
                reg.setHoraEntrada(null);
                if (!unica.isBefore(ALMUERZO_INICIO_MIN) && !unica.isAfter(ALMUERZO_INICIO_MAX)) {
                    reg.setInicioAlmuerzo(unica);
                } else if (!unica.isBefore(ALMUERZO_FIN_MIN) && !unica.isAfter(ALMUERZO_FIN_MAX)) {
                    reg.setFinAlmuerzo(unica);
                } else {
                    reg.setHoraSalida(unica);
                }
                return;
            }
        }

        // Columna I: horaEntrada - Primera marcación válida entre 05:00:00 y 11:30:00
        LocalTime entrada = null;
        for (LocalTime t : horas) {
            if (!t.isBefore(ENTRADA_MIN) && !t.isAfter(ENTRADA_MAX)) {
                entrada = t;
                break;
            }
        }
        reg.setHoraEntrada(entrada);

        // Columna J: inicioAlmuerzo - Primera marcación válida entre 12:00:00 y 13:35:00
        LocalTime iniAlm = null;
        for (LocalTime t : horas) {
            if (t.equals(entrada)) continue;
            if (!t.isBefore(ALMUERZO_INICIO_MIN) && !t.isAfter(ALMUERZO_INICIO_MAX)) {
                iniAlm = t;
                break;
            }
        }
        reg.setInicioAlmuerzo(iniAlm);

        // Columna K: finAlmuerzo - Última marcación válida entre 13:35:01 y 15:30:00
        LocalTime finAlm = null;
        for (int i = horas.size() - 1; i >= 0; i--) {
            LocalTime t = horas.get(i);
            if (t.equals(entrada) || t.equals(iniAlm)) continue;
            if (!t.isBefore(ALMUERZO_FIN_MIN) && !t.isAfter(ALMUERZO_FIN_MAX)) {
                finAlm = t;
                break;
            }
        }
        reg.setFinAlmuerzo(finAlm);

        // Columna L: horaSalida
        // Última marcación válida ocurrida entre 15:30:01 y 23:59:59
        // (o entre 11:30:01 y 13:30:00 si solo trabajó medio turno matutino y no hay timbradas posteriores)
        LocalTime salida = null;
        for (int i = horas.size() - 1; i >= 0; i--) {
            LocalTime t = horas.get(i);
            if (t.equals(entrada) || t.equals(iniAlm) || t.equals(finAlm)) continue;
            if (!t.isBefore(SALIDA_TARDE_MIN)) {
                salida = t;
                break;
            }
        }

        // Si no hubo salida después de las 15:30:01, verificar si es retiro de medio turno matutino
        if (salida == null && entrada != null) {
            for (int i = horas.size() - 1; i >= 0; i--) {
                LocalTime t = horas.get(i);
                if (t.isAfter(entrada) && !t.isBefore(LocalTime.of(11, 30, 1)) && !t.isAfter(LocalTime.of(13, 30, 0))) {
                    salida = t;
                    // Si se había asignado como inicioAlmuerzo, lo reasignamos a salida definitiva del medio turno
                    if (t.equals(reg.getInicioAlmuerzo())) {
                        reg.setInicioAlmuerzo(null);
                    }
                    break;
                }
            }
        }

        reg.setHoraSalida(salida);
    }

    /**
     * 3. FÓRMULAS AUDITADAS: CONDICIÓN DE INGRESO Y MINUTOS DE TARDANZA
     *
     * - Columna M:
     *   * horaEntrada == null -> "Sin Marcación (Omisión Entrada)"
     *   * horaEntrada <= 08:10:00 -> "A Tiempo"
     *   * horaEntrada > 08:10:00 -> "Tardanza"
     *
     * - Columna N (Regla de Tolerancia Retroactiva):
     *   * horaEntrada == null o horaEntrada <= 08:10:00 -> 0
     *   * horaEntrada > 08:10:00 -> Math.round( ChronoUnit.SECONDS.between(08:00:00, horaEntrada) / 60.0 )
     *     (se calcula contra las 08:00:00, no contra las 08:10:00).
     */
    private void evaluarCondicionIngresoYTardanza(RegistroAsistencia reg) {
        LocalTime entrada = reg.getHoraEntrada();

        if (entrada == null) {
            reg.setCondicionIngreso("Sin Marcación (Omisión Entrada)");
            reg.setMinutosTardanza(0L);
            return;
        }

        if (!entrada.isAfter(HORA_TOLERANCIA_MAX)) {
            // Entrada entre 05:00:00 y 08:10:00 (A Tiempo / Dentro de tolerancia)
            reg.setCondicionIngreso("A Tiempo");
            reg.setMinutosTardanza(0L);
        } else {
            // Entrada posterior a las 08:10:00 (Tardanza después de tolerancia de 10 min)
            // La tolerancia hasta las 08:10:00 no afecta el salario; solo los minutos posteriores
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
     * 4. JORNADA EFECTIVA, ALMUERZO Y BALANCE
     *
     * - Columna O:
     *   * Si horaEntrada == null o horaSalida == null o horaSalida <= horaEntrada -> jornadaEfectiva = null, horasEfectivas = 0.0
     *   * duracionBruta = horaSalida - horaEntrada
     *   * Descuento Almuerzo:
     *     - Si falta inicio o fin de almuerzo y salida >= 15:30 -> descuento fijo de 1 hora (13:00 a 14:00)
     *     - Si salida < 14:00 -> descuento almuerzo = 0
     *     - Si existe almuerzo real (J:K) dentro de I:L -> duracion real (si está entre 15 min y 2 horas, de lo contrario 1 hora fija)
     *   * jornadaEfectiva = Math.max(0, duracionBruta - descuentoAlmuerzo)
     *
     * - Columna P:
     *   * jornadaNorma = "08:00:00" (8.0 horas)
     *
     * - Columna Q:
     *   * Si jornadaEfectiva == null -> balanceJornada = null
     *   * Si existe -> ROUND(horasEfectivasDecimal - 8.0, 2)
     */
    private void calcularJornadasYBalance(RegistroAsistencia reg) {
        reg.setJornadaNorma(JORNADA_NORMA_TEXTO);
        reg.setHorasNormaDecimal(JORNADA_NORMA_HORAS);

        LocalTime entrada = reg.getHoraEntrada();
        LocalTime salida = reg.getHoraSalida();

        // Si falta entrada o salida, o salida es anterior a entrada -> jornada incompleta
        if (entrada == null || salida == null || !salida.isAfter(entrada)) {
            reg.setJornadaEfectiva(null);
            reg.setHorasEfectivasDecimal(0.0);
            reg.setBalanceJornada(null); // Evita error #¡VALOR!
            reg.setAlmuerzoImputado(false);
            return;
        }

        long duracionBrutaSegundos = Duration.between(entrada, salida).getSeconds();
        long descuentoAlmuerzoSegundos = 0L;
        boolean imputado = false;

        LocalTime iniAlm = reg.getInicioAlmuerzo();
        LocalTime finAlm = reg.getFinAlmuerzo();

        // REGLAS DE REFRIGERIO (ALMUERZO):
        if (!salida.isBefore(UMBRAL_MEDIA_JORNADA)) {
            // Trabajador laboró después de las 14:00:00
            if (iniAlm != null && finAlm != null && finAlm.isAfter(iniAlm)
                    && !iniAlm.isBefore(entrada) && !finAlm.isAfter(salida)) {
                // Almuerzo timbrado dentro de la jornada
                long segReal = Duration.between(iniAlm, finAlm).getSeconds();
                // Protección contra almuerzos irreales (menor a 15 min o mayor a 2 horas)
                if (segReal >= 900 && segReal <= 7200) {
                    descuentoAlmuerzoSegundos = segReal;
                    imputado = false;
                } else {
                    // Almuerzo anómalo: aplicar reglamentario de 1 hora
                    descuentoAlmuerzoSegundos = 3600L;
                    imputado = true;
                }
            } else if (!salida.isBefore(SALIDA_TARDE_MIN)) {
                // Falta inicio o fin de almuerzo y salió en la tarde (>= 15:30): descuento fijo reglamentario de 1 hora
                descuentoAlmuerzoSegundos = 3600L;
                imputado = true;
                // Asignar horas reglamentarias para trazabilidad visual si están vacías
                if (iniAlm == null && finAlm == null) {
                    reg.setInicioAlmuerzo(LocalTime.of(13, 0, 0));
                    reg.setFinAlmuerzo(LocalTime.of(14, 0, 0));
                }
            }
        } else {
            // Se retiró antes de las 14:00 (media jornada): descuento de almuerzo es 0
            descuentoAlmuerzoSegundos = 0L;
            imputado = false;
        }

        reg.setAlmuerzoImputado(imputado);

        long segundosEfectivos = Math.max(0L, duracionBrutaSegundos - descuentoAlmuerzoSegundos);

        // Formato HH:mm:ss
        long horas = segundosEfectivos / 3600;
        long minutos = (segundosEfectivos % 3600) / 60;
        long segundos = segundosEfectivos % 60;
        reg.setJornadaEfectiva(String.format("%02d:%02d:%02d", horas, minutos, segundos));

        // Horas efectivas decimales redondeadas a 2 decimales
        double horasDecimal = BigDecimal.valueOf(segundosEfectivos / 3600.0)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        reg.setHorasEfectivasDecimal(horasDecimal);

        // Balance de jornada: ROUND((horasEfectivasDecimal - 8.0), 2)
        double balance = BigDecimal.valueOf(horasDecimal - JORNADA_NORMA_HORAS)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        reg.setBalanceJornada(balance);
    }

    /**
     * 5. ESTADO DE CUMPLIMIENTO (Columna R)
     *
     * - Si jornadaEfectiva == null -> "Incompleto (Falta Salida/Entrada)"
     * - Si balanceJornada > 0.08 -> "Excedente de Jornada"
     * - Si balanceJornada < -0.08 -> "Déficit de Jornada"
     * - En caso contrario (entre -0.08 y +0.08) -> "Jornada Reglamentaria"
     */
    private void evaluarEstadoCumplimiento(RegistroAsistencia reg) {
        if (reg.getJornadaEfectiva() == null || reg.getBalanceJornada() == null) {
            reg.setEstadoCumplimiento("Incompleto (Falta Salida/Entrada)");
            return;
        }

        double balance = reg.getBalanceJornada();
        if (balance > TOLERANCIA_BALANCE) {
            reg.setEstadoCumplimiento("Excedente de Jornada");
        } else if (balance < -TOLERANCIA_BALANCE) {
            reg.setEstadoCumplimiento("Déficit de Jornada");
        } else {
            reg.setEstadoCumplimiento("Jornada Reglamentaria");
        }
    }
}

