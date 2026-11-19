package pe.gob.prorridre.asistencia.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO con el mapeo exacto de las 18 columnas auditadas para consumo en el Frontend / API REST:
 * Col A: empId
 * Col B: nombres
 * Col C: apellidos
 * Col D: empresa
 * Col E: departamento
 * Col F: fecha
 * Col G: numMarcaciones
 * Col H: horasCrudas
 * Col I: horaEntrada
 * Col J: inicioAlmuerzo
 * Col K: finAlmuerzo
 * Col L: horaSalida
 * Col M: condicionIngreso
 * Col N: minutosTardanza
 * Col O: jornadaEfectiva
 * Col P: jornadaNorma
 * Col Q: balanceJornada
 * Col R: estadoCumplimiento
 */
public class RegistroAsistenciaDto {

    private Long id;
    private String empId;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private String empresa;
    private String departamento;
    private LocalDate fecha;
    private Integer numMarcaciones;
    private String horasCrudas;
    private LocalTime horaEntrada;
    private LocalTime inicioAlmuerzo;
    private LocalTime finAlmuerzo;
    private LocalTime horaSalida;
    private String condicionIngreso;
    private Long minutosTardanza;
    private String jornadaEfectiva;
    private Double horasEfectivasDecimal;
    private String jornadaNorma;
    private Double horasNormaDecimal;
    private Double balanceJornada;
    private String estadoCumplimiento;
    private Boolean almuerzoImputado;
    private String estadoJustificacion;
    private String observacionDoctor;
    private Long minutosTardanzaOriginal;
    private Double balanceJornadaOriginal;

    public RegistroAsistenciaDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getNombreCompleto() {
        if (nombreCompleto != null && !nombreCompleto.isEmpty()) return nombreCompleto;
        return ((nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "")).trim();
    }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

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
}

