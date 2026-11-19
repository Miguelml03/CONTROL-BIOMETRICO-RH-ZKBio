package pe.gob.prorridre.asistencia.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO con todos los KPIs del dashboard calculados en tiempo real.
 * Respuesta de GET /api/v1/dashboard/resumen
 */
public class DashboardKpiDto {

    // ── Totales generales ────────────────────────────────────────────────────
    private long totalRegistrosEvaluados;
    private long totalEmpleadosActivos;
    private long totalDiasHabilesRango;

    // ── Ausentismo Real (Días-Hombre) ─────────────────────────────────────
    /** Pares empId+fecha donde el empleado NO tiene ninguna marcación en un día hábil */
    private long diasHombreFalta;
    /** Lista de registros de ausencia (empId, nombre, departamento, fecha) */
    private List<AusenciaDto> detalleAusencias = new ArrayList<>();

    // ── Alerta de Empleados Desconocidos ───────────────────────────────────
    private long countEmpleadosDesconocidos;
    private List<EmpleadoDesconocidoDto> detalleEmpleadosDesconocidos = new ArrayList<>();

    // ── Puntualidad ───────────────────────────────────────────────────────
    private long countATiempo;
    private long countTardanza;
    private long countOmisionEntrada;
    private double pctATiempo;
    private double pctTardanza;
    private double pctOmisionEntrada;

    // ── Tardanzas ─────────────────────────────────────────────────────────
    private long totalMinutosTardanza;
    private double promedioMinutosTardanza;

    // ── Cumplimiento de jornada ──────────────────────────────────────────
    private long countExcedente;
    private long countReglamentaria;
    private long countDeficit;
    private long countIncompleto;
    private double pctExcedente;
    private double pctReglamentaria;
    private double pctDeficit;
    private double pctIncompleto;

    // ── Horas totales ─────────────────────────────────────────────────────
    private double totalHorasEfectivas;
    private double totalHorasExtra;      // suma de balances > 0
    private double totalHorasDeficit;    // suma de |balance| donde balance < 0

    // ── Ranking por departamento ───────────────────────────────────────────
    private List<RankingDepartamentoDto> rankingDepartamentos = new ArrayList<>();

    // ── Rango de fechas usado ─────────────────────────────────────────────
    private String fechaInicio;
    private String fechaFin;
    private String departamentoFiltro;
    private String empIdFiltro;

    public DashboardKpiDto() {}

    // ── DTOs anidados ────────────────────────────────────────────────────

    public static class AusenciaDto {
        private String empId;
        private String nombreCompleto;
        private String departamento;
        private String fecha;

        public AusenciaDto() {}
        public AusenciaDto(String empId, String nombreCompleto, String departamento, String fecha) {
            this.empId = empId;
            this.nombreCompleto = nombreCompleto;
            this.departamento = departamento;
            this.fecha = fecha;
        }

        public String getEmpId() { return empId; }
        public void setEmpId(String empId) { this.empId = empId; }
        public String getNombreCompleto() { return nombreCompleto; }
        public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
        public String getDepartamento() { return departamento; }
        public void setDepartamento(String departamento) { this.departamento = departamento; }
        public String getFecha() { return fecha; }
        public void setFecha(String fecha) { this.fecha = fecha; }
    }

    public static class EmpleadoDesconocidoDto {
        private String empId;
        private String nombres;
        private String apellidos;
        private String departamento;
        private String mensaje;

        public EmpleadoDesconocidoDto() {}
        public EmpleadoDesconocidoDto(String empId, String nombres, String apellidos, String departamento, String mensaje) {
            this.empId = empId;
            this.nombres = nombres;
            this.apellidos = apellidos;
            this.departamento = departamento;
            this.mensaje = mensaje;
        }

        public String getEmpId() { return empId; }
        public void setEmpId(String empId) { this.empId = empId; }
        public String getNombres() { return nombres; }
        public void setNombres(String nombres) { this.nombres = nombres; }
        public String getApellidos() { return apellidos; }
        public void setApellidos(String apellidos) { this.apellidos = apellidos; }
        public String getDepartamento() { return departamento; }
        public void setDepartamento(String departamento) { this.departamento = departamento; }
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    }

    public static class RankingDepartamentoDto {
        private String departamento;
        private long empleados;
        private long registros;
        private double horasEfectivas;
        private double promedioHorasDia;
        private long totalMinutosTardanza;
        private long faltas;
        private long tardanzas;
        private long aTiempo;
        private long incompletos;

        public RankingDepartamentoDto() {}

        public String getDepartamento() { return departamento; }
        public void setDepartamento(String departamento) { this.departamento = departamento; }
        public long getEmpleados() { return empleados; }
        public void setEmpleados(long empleados) { this.empleados = empleados; }
        public long getRegistros() { return registros; }
        public void setRegistros(long registros) { this.registros = registros; }
        public double getHorasEfectivas() { return horasEfectivas; }
        public void setHorasEfectivas(double horasEfectivas) { this.horasEfectivas = horasEfectivas; }
        public double getPromedioHorasDia() { return promedioHorasDia; }
        public void setPromedioHorasDia(double promedioHorasDia) { this.promedioHorasDia = promedioHorasDia; }
        public long getTotalMinutosTardanza() { return totalMinutosTardanza; }
        public void setTotalMinutosTardanza(long totalMinutosTardanza) { this.totalMinutosTardanza = totalMinutosTardanza; }
        public long getFaltas() { return faltas; }
        public void setFaltas(long faltas) { this.faltas = faltas; }
        public long getTardanzas() { return tardanzas; }
        public void setTardanzas(long tardanzas) { this.tardanzas = tardanzas; }
        public long getaTiempo() { return aTiempo; }
        public void setaTiempo(long aTiempo) { this.aTiempo = aTiempo; }
        public long getIncompletos() { return incompletos; }
        public void setIncompletos(long incompletos) { this.incompletos = incompletos; }
    }

    // ── Getters y Setters principales ─────────────────────────────────────

    public long getTotalRegistrosEvaluados() { return totalRegistrosEvaluados; }
    public void setTotalRegistrosEvaluados(long totalRegistrosEvaluados) { this.totalRegistrosEvaluados = totalRegistrosEvaluados; }
    public long getTotalEmpleadosActivos() { return totalEmpleadosActivos; }
    public void setTotalEmpleadosActivos(long totalEmpleadosActivos) { this.totalEmpleadosActivos = totalEmpleadosActivos; }
    public long getTotalDiasHabilesRango() { return totalDiasHabilesRango; }
    public void setTotalDiasHabilesRango(long totalDiasHabilesRango) { this.totalDiasHabilesRango = totalDiasHabilesRango; }
    public long getDiasHombreFalta() { return diasHombreFalta; }
    public void setDiasHombreFalta(long diasHombreFalta) { this.diasHombreFalta = diasHombreFalta; }
    public List<AusenciaDto> getDetalleAusencias() { return detalleAusencias; }
    public void setDetalleAusencias(List<AusenciaDto> detalleAusencias) { this.detalleAusencias = detalleAusencias; }

    public long getCountEmpleadosDesconocidos() { return countEmpleadosDesconocidos; }
    public void setCountEmpleadosDesconocidos(long countEmpleadosDesconocidos) { this.countEmpleadosDesconocidos = countEmpleadosDesconocidos; }
    public List<EmpleadoDesconocidoDto> getDetalleEmpleadosDesconocidos() { return detalleEmpleadosDesconocidos; }
    public void setDetalleEmpleadosDesconocidos(List<EmpleadoDesconocidoDto> detalleEmpleadosDesconocidos) { this.detalleEmpleadosDesconocidos = detalleEmpleadosDesconocidos; }

    public long getCountATiempo() { return countATiempo; }
    public void setCountATiempo(long countATiempo) { this.countATiempo = countATiempo; }
    public long getCountTardanza() { return countTardanza; }
    public void setCountTardanza(long countTardanza) { this.countTardanza = countTardanza; }
    public long getCountOmisionEntrada() { return countOmisionEntrada; }
    public void setCountOmisionEntrada(long countOmisionEntrada) { this.countOmisionEntrada = countOmisionEntrada; }
    public double getPctATiempo() { return pctATiempo; }
    public void setPctATiempo(double pctATiempo) { this.pctATiempo = pctATiempo; }
    public double getPctTardanza() { return pctTardanza; }
    public void setPctTardanza(double pctTardanza) { this.pctTardanza = pctTardanza; }
    public double getPctOmisionEntrada() { return pctOmisionEntrada; }
    public void setPctOmisionEntrada(double pctOmisionEntrada) { this.pctOmisionEntrada = pctOmisionEntrada; }
    public long getTotalMinutosTardanza() { return totalMinutosTardanza; }
    public void setTotalMinutosTardanza(long totalMinutosTardanza) { this.totalMinutosTardanza = totalMinutosTardanza; }
    public double getPromedioMinutosTardanza() { return promedioMinutosTardanza; }
    public void setPromedioMinutosTardanza(double promedioMinutosTardanza) { this.promedioMinutosTardanza = promedioMinutosTardanza; }
    public long getCountExcedente() { return countExcedente; }
    public void setCountExcedente(long countExcedente) { this.countExcedente = countExcedente; }
    public long getCountReglamentaria() { return countReglamentaria; }
    public void setCountReglamentaria(long countReglamentaria) { this.countReglamentaria = countReglamentaria; }
    public long getCountDeficit() { return countDeficit; }
    public void setCountDeficit(long countDeficit) { this.countDeficit = countDeficit; }
    public long getCountIncompleto() { return countIncompleto; }
    public void setCountIncompleto(long countIncompleto) { this.countIncompleto = countIncompleto; }
    public double getPctExcedente() { return pctExcedente; }
    public void setPctExcedente(double pctExcedente) { this.pctExcedente = pctExcedente; }
    public double getPctReglamentaria() { return pctReglamentaria; }
    public void setPctReglamentaria(double pctReglamentaria) { this.pctReglamentaria = pctReglamentaria; }
    public double getPctDeficit() { return pctDeficit; }
    public void setPctDeficit(double pctDeficit) { this.pctDeficit = pctDeficit; }
    public double getPctIncompleto() { return pctIncompleto; }
    public void setPctIncompleto(double pctIncompleto) { this.pctIncompleto = pctIncompleto; }
    public double getTotalHorasEfectivas() { return totalHorasEfectivas; }
    public void setTotalHorasEfectivas(double totalHorasEfectivas) { this.totalHorasEfectivas = totalHorasEfectivas; }
    public double getTotalHorasExtra() { return totalHorasExtra; }
    public void setTotalHorasExtra(double totalHorasExtra) { this.totalHorasExtra = totalHorasExtra; }
    public double getTotalHorasDeficit() { return totalHorasDeficit; }
    public void setTotalHorasDeficit(double totalHorasDeficit) { this.totalHorasDeficit = totalHorasDeficit; }
    public List<RankingDepartamentoDto> getRankingDepartamentos() { return rankingDepartamentos; }
    public void setRankingDepartamentos(List<RankingDepartamentoDto> rankingDepartamentos) { this.rankingDepartamentos = rankingDepartamentos; }
    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }
    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }
    public String getDepartamentoFiltro() { return departamentoFiltro; }
    public void setDepartamentoFiltro(String departamentoFiltro) { this.departamentoFiltro = departamentoFiltro; }
    public String getEmpIdFiltro() { return empIdFiltro; }
    public void setEmpIdFiltro(String empIdFiltro) { this.empIdFiltro = empIdFiltro; }
}
