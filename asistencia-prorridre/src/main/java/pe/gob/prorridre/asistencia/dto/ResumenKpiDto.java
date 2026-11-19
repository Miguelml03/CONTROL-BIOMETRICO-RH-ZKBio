package pe.gob.prorridre.asistencia.dto;

import java.time.LocalDate;

public class ResumenKpiDto {

    private LocalDate fecha;
    private long totalRegistros;
    private long aTiempo;
    private long tardanzas;
    private long omisionEntrada;
    private long incompletos;
    private long excedenteJornada;
    private long deficitJornada;
    private long jornadaReglamentaria;
    private long totalMinutosTardanza;
    private double totalHorasEfectivas;
    private double promedioHorasEfectivas;

    public ResumenKpiDto() {}

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public long getTotalRegistros() { return totalRegistros; }
    public void setTotalRegistros(long totalRegistros) { this.totalRegistros = totalRegistros; }

    public long getaTiempo() { return aTiempo; }
    public void setaTiempo(long aTiempo) { this.aTiempo = aTiempo; }

    public long getTardanzas() { return tardanzas; }
    public void setTardanzas(long tardanzas) { this.tardanzas = tardanzas; }

    public long getOmisionEntrada() { return omisionEntrada; }
    public void setOmisionEntrada(long omisionEntrada) { this.omisionEntrada = omisionEntrada; }

    public long getIncompletos() { return incompletos; }
    public void setIncompletos(long incompletos) { this.incompletos = incompletos; }

    public long getExcedenteJornada() { return excedenteJornada; }
    public void setExcedenteJornada(long excedenteJornada) { this.excedenteJornada = excedenteJornada; }

    public long getDeficitJornada() { return deficitJornada; }
    public void setDeficitJornada(long deficitJornada) { this.deficitJornada = deficitJornada; }

    public long getJornadaReglamentaria() { return jornadaReglamentaria; }
    public void setJornadaReglamentaria(long jornadaReglamentaria) { this.jornadaReglamentaria = jornadaReglamentaria; }

    public long getTotalMinutosTardanza() { return totalMinutosTardanza; }
    public void setTotalMinutosTardanza(long totalMinutosTardanza) { this.totalMinutosTardanza = totalMinutosTardanza; }

    public double getTotalHorasEfectivas() { return totalHorasEfectivas; }
    public void setTotalHorasEfectivas(double totalHorasEfectivas) { this.totalHorasEfectivas = totalHorasEfectivas; }

    public double getPromedioHorasEfectivas() { return promedioHorasEfectivas; }
    public void setPromedioHorasEfectivas(double promedioHorasEfectivas) { this.promedioHorasEfectivas = promedioHorasEfectivas; }
}

