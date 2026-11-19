package pe.gob.prorridre.asistencia.dto;

public class JustificarRequestDto {

    private String tipoJustificacion;
    private String observacion;
    private String aplicadoPor = "DOCTOR";

    public JustificarRequestDto() {}

    public JustificarRequestDto(String tipoJustificacion, String observacion) {
        this.tipoJustificacion = tipoJustificacion;
        this.observacion = observacion;
    }

    public String getTipoJustificacion() { return tipoJustificacion; }
    public void setTipoJustificacion(String tipoJustificacion) { this.tipoJustificacion = tipoJustificacion; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public String getAplicadoPor() { return aplicadoPor; }
    public void setAplicadoPor(String aplicadoPor) { this.aplicadoPor = aplicadoPor; }
}

