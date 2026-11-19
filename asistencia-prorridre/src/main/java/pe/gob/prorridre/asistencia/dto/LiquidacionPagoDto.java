package pe.gob.prorridre.asistencia.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de liquidación mensual por trabajador.
 * Respuesta de GET /api/v1/planillas y GET /api/v1/planillas/{empId}
 */
public class LiquidacionPagoDto {

    // ── Identificación ────────────────────────────────────────────────────
    private String empId;
    private String nombreCompleto;
    private String departamento;
    private String cargo;
    private String periodoInicio;
    private String periodoFin;

    // ── Configuración salarial ─────────────────────────────────────────
    private BigDecimal sueldoMensual;
    private BigDecimal valorDia;
    private BigDecimal valorHora;
    private BigDecimal valorMinuto;
    private Boolean excedenteCompensaDeficit;

    // ── Acumuladores de asistencia en el período ──────────────────────
    private int diasEvaluados;
    private int diasConRegistro;
    private int diasFalta;             // sin ninguna marcación en día hábil
    private long totalMinutosTardanza;
    private long minutosJustificados;
    private long minutosTardanzaNetosNoJustificados;
    private double totalHorasEfectivas;
    private double totalHorasExcedente;  // suma de balances > 0
    private double totalHorasDeficit;    // suma de |balance| donde balance < 0
    private double horasDeficitNetas;    // después de compensación si aplica
    private int cantidadOmisionMarcado; // registros con omisión de entrada

    // ── Descuentos calculados ─────────────────────────────────────────
    private BigDecimal descuentoTardanzas;
    private BigDecimal descuentoDeficitHoras;
    private BigDecimal descuentoFaltas;
    private BigDecimal penalidadOmisionMarcado;
    private BigDecimal totalDescuentos;

    // ── Bonificaciones ────────────────────────────────────────────────
    /** Pago por horas extra (si excedenteCompensaDeficit = false) */
    private BigDecimal bonificacionHorasExtra;

    // ── Neto a pagar ─────────────────────────────────────────────────
    private BigDecimal netoAPagar;

    // ── Detalle de justificaciones aplicadas ─────────────────────────
    private List<JustificacionResumenDto> justificaciones;

    public LiquidacionPagoDto() {}

    // ── DTO anidado de justificación ─────────────────────────────────

    public static class JustificacionResumenDto {
        private Long registroId;
        private String fecha;
        private String tipoJustificacion;
        private String observacion;
        private long minutosExonerados;
        private double horasExoneradas;
        private String aplicadoPor;
        private String creadoEn;

        public JustificacionResumenDto() {}

        public Long getRegistroId() { return registroId; }
        public void setRegistroId(Long registroId) { this.registroId = registroId; }
        public String getFecha() { return fecha; }
        public void setFecha(String fecha) { this.fecha = fecha; }
        public String getTipoJustificacion() { return tipoJustificacion; }
        public void setTipoJustificacion(String tipoJustificacion) { this.tipoJustificacion = tipoJustificacion; }
        public String getObservacion() { return observacion; }
        public void setObservacion(String observacion) { this.observacion = observacion; }
        public long getMinutosExonerados() { return minutosExonerados; }
        public void setMinutosExonerados(long minutosExonerados) { this.minutosExonerados = minutosExonerados; }
        public double getHorasExoneradas() { return horasExoneradas; }
        public void setHorasExoneradas(double horasExoneradas) { this.horasExoneradas = horasExoneradas; }
        public String getAplicadoPor() { return aplicadoPor; }
        public void setAplicadoPor(String aplicadoPor) { this.aplicadoPor = aplicadoPor; }
        public String getCreadoEn() { return creadoEn; }
        public void setCreadoEn(String creadoEn) { this.creadoEn = creadoEn; }
    }

    // ── Getters y Setters ─────────────────────────────────────────────

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(String periodoInicio) { this.periodoInicio = periodoInicio; }
    public String getPeriodoFin() { return periodoFin; }
    public void setPeriodoFin(String periodoFin) { this.periodoFin = periodoFin; }
    public BigDecimal getSueldoMensual() { return sueldoMensual; }
    public void setSueldoMensual(BigDecimal sueldoMensual) { this.sueldoMensual = sueldoMensual; }
    public BigDecimal getValorDia() { return valorDia; }
    public void setValorDia(BigDecimal valorDia) { this.valorDia = valorDia; }
    public BigDecimal getValorHora() { return valorHora; }
    public void setValorHora(BigDecimal valorHora) { this.valorHora = valorHora; }
    public BigDecimal getValorMinuto() { return valorMinuto; }
    public void setValorMinuto(BigDecimal valorMinuto) { this.valorMinuto = valorMinuto; }
    public Boolean getExcedenteCompensaDeficit() { return excedenteCompensaDeficit; }
    public void setExcedenteCompensaDeficit(Boolean excedenteCompensaDeficit) { this.excedenteCompensaDeficit = excedenteCompensaDeficit; }
    public int getDiasEvaluados() { return diasEvaluados; }
    public void setDiasEvaluados(int diasEvaluados) { this.diasEvaluados = diasEvaluados; }
    public int getDiasConRegistro() { return diasConRegistro; }
    public void setDiasConRegistro(int diasConRegistro) { this.diasConRegistro = diasConRegistro; }
    public int getDiasFalta() { return diasFalta; }
    public void setDiasFalta(int diasFalta) { this.diasFalta = diasFalta; }
    public long getTotalMinutosTardanza() { return totalMinutosTardanza; }
    public void setTotalMinutosTardanza(long totalMinutosTardanza) { this.totalMinutosTardanza = totalMinutosTardanza; }
    public long getMinutosJustificados() { return minutosJustificados; }
    public void setMinutosJustificados(long minutosJustificados) { this.minutosJustificados = minutosJustificados; }
    public long getMinutosTardanzaNetosNoJustificados() { return minutosTardanzaNetosNoJustificados; }
    public void setMinutosTardanzaNetosNoJustificados(long minutosTardanzaNetosNoJustificados) { this.minutosTardanzaNetosNoJustificados = minutosTardanzaNetosNoJustificados; }
    public double getTotalHorasEfectivas() { return totalHorasEfectivas; }
    public void setTotalHorasEfectivas(double totalHorasEfectivas) { this.totalHorasEfectivas = totalHorasEfectivas; }
    public double getTotalHorasExcedente() { return totalHorasExcedente; }
    public void setTotalHorasExcedente(double totalHorasExcedente) { this.totalHorasExcedente = totalHorasExcedente; }
    public double getTotalHorasDeficit() { return totalHorasDeficit; }
    public void setTotalHorasDeficit(double totalHorasDeficit) { this.totalHorasDeficit = totalHorasDeficit; }
    public double getHorasDeficitNetas() { return horasDeficitNetas; }
    public void setHorasDeficitNetas(double horasDeficitNetas) { this.horasDeficitNetas = horasDeficitNetas; }
    public int getCantidadOmisionMarcado() { return cantidadOmisionMarcado; }
    public void setCantidadOmisionMarcado(int cantidadOmisionMarcado) { this.cantidadOmisionMarcado = cantidadOmisionMarcado; }
    public BigDecimal getDescuentoTardanzas() { return descuentoTardanzas; }
    public void setDescuentoTardanzas(BigDecimal descuentoTardanzas) { this.descuentoTardanzas = descuentoTardanzas; }
    public BigDecimal getDescuentoDeficitHoras() { return descuentoDeficitHoras; }
    public void setDescuentoDeficitHoras(BigDecimal descuentoDeficitHoras) { this.descuentoDeficitHoras = descuentoDeficitHoras; }
    public BigDecimal getDescuentoFaltas() { return descuentoFaltas; }
    public void setDescuentoFaltas(BigDecimal descuentoFaltas) { this.descuentoFaltas = descuentoFaltas; }
    public BigDecimal getPenalidadOmisionMarcado() { return penalidadOmisionMarcado; }
    public void setPenalidadOmisionMarcado(BigDecimal penalidadOmisionMarcado) { this.penalidadOmisionMarcado = penalidadOmisionMarcado; }
    public BigDecimal getTotalDescuentos() { return totalDescuentos; }
    public void setTotalDescuentos(BigDecimal totalDescuentos) { this.totalDescuentos = totalDescuentos; }
    public BigDecimal getBonificacionHorasExtra() { return bonificacionHorasExtra; }
    public void setBonificacionHorasExtra(BigDecimal bonificacionHorasExtra) { this.bonificacionHorasExtra = bonificacionHorasExtra; }
    public BigDecimal getNetoAPagar() { return netoAPagar; }
    public void setNetoAPagar(BigDecimal netoAPagar) { this.netoAPagar = netoAPagar; }
    public List<JustificacionResumenDto> getJustificaciones() { return justificaciones; }
    public void setJustificaciones(List<JustificacionResumenDto> justificaciones) { this.justificaciones = justificaciones; }
}

