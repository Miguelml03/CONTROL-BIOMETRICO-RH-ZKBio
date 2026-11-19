package pe.gob.prorridre.asistencia.dto;

import java.math.BigDecimal;

public class ConfigFinancieraRequestDto {

    private String empId;
    private BigDecimal sueldoMensual;
    private Integer diasBaseMes;
    private Integer horasBaseDia;
    private BigDecimal multaOmision;
    private Boolean excedenteCompensaDeficit;
    private String cargo;
    private String actualizadoPor = "DOCTOR";

    public ConfigFinancieraRequestDto() {}

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public BigDecimal getSueldoMensual() { return sueldoMensual; }
    public void setSueldoMensual(BigDecimal sueldoMensual) { this.sueldoMensual = sueldoMensual; }

    public Integer getDiasBaseMes() { return diasBaseMes; }
    public void setDiasBaseMes(Integer diasBaseMes) { this.diasBaseMes = diasBaseMes; }

    public Integer getHorasBaseDia() { return horasBaseDia; }
    public void setHorasBaseDia(Integer horasBaseDia) { this.horasBaseDia = horasBaseDia; }

    public BigDecimal getMultaOmision() { return multaOmision; }
    public void setMultaOmision(BigDecimal multaOmision) { this.multaOmision = multaOmision; }

    public Boolean getExcedenteCompensaDeficit() { return excedenteCompensaDeficit; }
    public void setExcedenteCompensaDeficit(Boolean excedenteCompensaDeficit) { this.excedenteCompensaDeficit = excedenteCompensaDeficit; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
}

