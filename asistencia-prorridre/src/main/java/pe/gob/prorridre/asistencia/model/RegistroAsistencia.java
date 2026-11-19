package pe.gob.prorridre.asistencia.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entidad que representa la auditoría diaria de asistencia de un empleado.
 * Mapeo exacto de las 18 columnas auditadas del Excel (Columnas A:R):
 *
 * A: empId
 * B: nombres
 * C: apellidos
 * D: empresa
 * E: departamento
 * F: fecha
 * G: numMarcaciones
 * H: horasCrudas
 * I: horaEntrada
 * J: inicioAlmuerzo
 * K: finAlmuerzo
 * L: horaSalida
 * M: condicionIngreso
 * N: minutosTardanza
 * O: jornadaEfectiva (formato HH:mm:ss y decimal)
 * P: jornadaNorma (formato "08:00:00" y 8.0)
 * Q: balanceJornada (horas decimales con signo, null si incompleto)
 * R: estadoCumplimiento
 */
@Entity
@Table(name = "registro_asistencia",
       indexes = {
           @Index(name = "idx_reg_emp_fecha", columnList = "emp_id, fecha", unique = true),
           @Index(name = "idx_reg_fecha", columnList = "fecha"),
           @Index(name = "idx_reg_departamento", columnList = "departamento")
       })
public class RegistroAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Columna A: Código/ID del empleado en ZKBio Time */
    @Column(name = "emp_id", nullable = false, length = 50)
    private String empId;

    /** Columna B: Nombres del empleado */
    @Column(name = "nombres", length = 150)
    private String nombres;

    /** Columna C: Apellidos del empleado */
    @Column(name = "apellidos", length = 150)
    private String apellidos;

    /** Columna D: Empresa/Entidad (PRORRIDRE) */
    @Column(name = "empresa", length = 100)
    private String empresa = "PRORRIDRE";

    /** Columna E: Departamento / Meta / Oficina */
    @Column(name = "departamento", length = 150)
    private String departamento;

    /** Columna F: Fecha del registro de asistencia */
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /** Columna G: Número de marcaciones válidas tras filtro antirrebote (Debounce) */
    @Column(name = "num_marcaciones", nullable = false)
    private Integer numMarcaciones = 0;

    /** Columna H: Horas crudas registradas separadas por coma */
    @Column(name = "horas_crudas", length = 500)
    private String horasCrudas;

    /** Columna I: Hora de entrada asignada por ventana matutina */
    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    /** Columna J: Inicio de refrigerio / almuerzo */
    @Column(name = "inicio_almuerzo")
    private LocalTime inicioAlmuerzo;

    /** Columna K: Fin de refrigerio / almuerzo */
    @Column(name = "fin_almuerzo")
    private LocalTime finAlmuerzo;

    /** Columna L: Hora de salida definitiva */
    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    /** Columna M: Condición de ingreso ("A Tiempo", "Tardanza", "Sin Marcación (Omisión Entrada)") */
    @Column(name = "condicion_ingreso", length = 60)
    private String condicionIngreso;

    /** Columna N: Minutos de tardanza calculados contra las 08:00:00 tras tolerancia */
    @Column(name = "minutos_tardanza", nullable = false)
    private Long minutosTardanza = 0L;

    /** Columna O: Jornada efectiva en formato HH:mm:ss */
    @Column(name = "jornada_efectiva", length = 20)
    private String jornadaEfectiva;

    /** Columna O (decimal): Horas efectivas trabajadas */
    @Column(name = "horas_efectivas_decimal", nullable = false)
    private Double horasEfectivasDecimal = 0.0;

    /** Columna P: Jornada reglamentaria en formato HH:mm:ss ("08:00:00") */
    @Column(name = "jornada_norma", length = 20)
    private String jornadaNorma = "08:00:00";

    /** Columna P (decimal): Horas reglamentarias (8.0) */
    @Column(name = "horas_norma_decimal", nullable = false)
    private Double horasNormaDecimal = 8.0;

    /** Columna Q: Balance de jornada en horas decimales (horasEfectivas - 8.0) o null si incompleto */
    @Column(name = "balance_jornada")
    private Double balanceJornada;

    /** Columna R: Estado de cumplimiento ("Jornada Reglamentaria", "Excedente de Jornada", "Déficit de Jornada", "Incompleto...") */
    @Column(name = "estado_cumplimiento", length = 60)
    private String estadoCumplimiento;

    /** Indicador si se aplicó relleno o descuento reglamentario de 1 hora de almuerzo */
    @Column(name = "almuerzo_imputado")
    private Boolean almuerzoImputado = false;

    /** Estado de justificación aplicado por el Doctor ("NINGUNO", "JUSTIFICADO_MEDICO", etc.) */
    @Column(name = "estado_justificacion", length = 40)
    private String estadoJustificacion = "NINGUNO";

    /** Observación o nota del Doctor al autorizar la justificación */
    @Column(name = "observacion_doctor", length = 500)
    private String observacionDoctor;

    /** Tardanza original calculada por el biométrico antes de justificar */
    @Column(name = "minutos_tardanza_original")
    private Long minutosTardanzaOriginal;

    /** Balance de jornada original antes de justificar */
    @Column(name = "balance_jornada_original")
    private Double balanceJornadaOriginal;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    public RegistroAsistencia() {}

    /* ------------------------------------------------------------------
     * Getters y Setters
     * ---------------------------------------------------------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public Integer getNumMarcaciones() { return numMarcaciones; }
    public void setNumMarcaciones(Integer numMarcaciones) { this.numMarcaciones = numMarcaciones; }

    public String getHorasCrudas() { return horasCrudas; }
    public void setHorasCrudas(String horasCrudas) { this.horasCrudas = horasCrudas; }

    public LocalTime getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(LocalTime horaEntrada) { this.horaEntrada = horaEntrada; }

    public LocalTime getInicioAlmuerzo() { return inicioAlmuerzo; }
    public void setInicioAlmuerzo(LocalTime inicioAlmuerzo) { this.inicioAlmuerzo = inicioAlmuerzo; }

    public LocalTime getFinAlmuerzo() { return finAlmuerzo; }
    public void setFinAlmuerzo(LocalTime finAlmuerzo) { this.finAlmuerzo = finAlmuerzo; }

    public LocalTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalTime horaSalida) { this.horaSalida = horaSalida; }

    public String getCondicionIngreso() { return condicionIngreso; }
    public void setCondicionIngreso(String condicionIngreso) { this.condicionIngreso = condicionIngreso; }

    public Long getMinutosTardanza() { return minutosTardanza; }
    public void setMinutosTardanza(Long minutosTardanza) { this.minutosTardanza = minutosTardanza; }

    public String getJornadaEfectiva() { return jornadaEfectiva; }
    public void setJornadaEfectiva(String jornadaEfectiva) { this.jornadaEfectiva = jornadaEfectiva; }

    public Double getHorasEfectivasDecimal() { return horasEfectivasDecimal; }
    public void setHorasEfectivasDecimal(Double horasEfectivasDecimal) { this.horasEfectivasDecimal = horasEfectivasDecimal; }

    public String getJornadaNorma() { return jornadaNorma; }
    public void setJornadaNorma(String jornadaNorma) { this.jornadaNorma = jornadaNorma; }

    public Double getHorasNormaDecimal() { return horasNormaDecimal; }
    public void setHorasNormaDecimal(Double horasNormaDecimal) { this.horasNormaDecimal = horasNormaDecimal; }

    public Double getBalanceJornada() { return balanceJornada; }
    public void setBalanceJornada(Double balanceJornada) { this.balanceJornada = balanceJornada; }

    public String getEstadoCumplimiento() { return estadoCumplimiento; }
    public void setEstadoCumplimiento(String estadoCumplimiento) { this.estadoCumplimiento = estadoCumplimiento; }

    public Boolean getAlmuerzoImputado() { return almuerzoImputado; }
    public void setAlmuerzoImputado(Boolean almuerzoImputado) { this.almuerzoImputado = almuerzoImputado; }

    public String getEstadoJustificacion() { return estadoJustificacion; }
    public void setEstadoJustificacion(String estadoJustificacion) { this.estadoJustificacion = estadoJustificacion; }

    public String getObservacionDoctor() { return observacionDoctor; }
    public void setObservacionDoctor(String observacionDoctor) { this.observacionDoctor = observacionDoctor; }

    public Long getMinutosTardanzaOriginal() { return minutosTardanzaOriginal; }
    public void setMinutosTardanzaOriginal(Long minutosTardanzaOriginal) { this.minutosTardanzaOriginal = minutosTardanzaOriginal; }

    public Double getBalanceJornadaOriginal() { return balanceJornadaOriginal; }
    public void setBalanceJornadaOriginal(Double balanceJornadaOriginal) { this.balanceJornadaOriginal = balanceJornadaOriginal; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}

