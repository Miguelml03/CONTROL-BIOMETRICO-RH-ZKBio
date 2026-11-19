package pe.gob.prorridre.asistencia.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public class EditarAsistenciaRequestDto {

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime horaEntrada;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime inicioAlmuerzo;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime finAlmuerzo;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime horaSalida;

    private String horasCrudas;
    private String condicionIngreso;
    private Long minutosTardanza;
    private String observacionDoctor;
    private Boolean recalcularAutomatico = true;

    public EditarAsistenciaRequestDto() {}

    public LocalTime getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(LocalTime horaEntrada) { this.horaEntrada = horaEntrada; }

    public LocalTime getInicioAlmuerzo() { return inicioAlmuerzo; }
    public void setInicioAlmuerzo(LocalTime inicioAlmuerzo) { this.inicioAlmuerzo = inicioAlmuerzo; }

    public LocalTime getFinAlmuerzo() { return finAlmuerzo; }
    public void setFinAlmuerzo(LocalTime finAlmuerzo) { this.finAlmuerzo = finAlmuerzo; }

    public LocalTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalTime horaSalida) { this.horaSalida = horaSalida; }

    public String getHorasCrudas() { return horasCrudas; }
    public void setHorasCrudas(String horasCrudas) { this.horasCrudas = horasCrudas; }

    public String getCondicionIngreso() { return condicionIngreso; }
    public void setCondicionIngreso(String condicionIngreso) { this.condicionIngreso = condicionIngreso; }

    public Long getMinutosTardanza() { return minutosTardanza; }
    public void setMinutosTardanza(Long minutosTardanza) { this.minutosTardanza = minutosTardanza; }

    public String getObservacionDoctor() { return observacionDoctor; }
    public void setObservacionDoctor(String observacionDoctor) { this.observacionDoctor = observacionDoctor; }

    public Boolean getRecalcularAutomatico() { return recalcularAutomatico; }
    public void setRecalcularAutomatico(Boolean recalcularAutomatico) { this.recalcularAutomatico = recalcularAutomatico; }
}

