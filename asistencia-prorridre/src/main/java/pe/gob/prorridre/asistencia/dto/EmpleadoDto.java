package pe.gob.prorridre.asistencia.dto;

import java.time.LocalDate;

/**
 * DTO enriquecido para la transferencia de información de empleados.
 */
public class EmpleadoDto {

    private String empId;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private String cargo;
    private LocalDate fechaContratacion;
    private String empresa;
    private Long departamentoId;
    private String departamentoNombre;
    private String departamentoRuta;
    private java.math.BigDecimal sueldoMensual;
    private Boolean activo;
    private Boolean desconocido;

    public EmpleadoDto() {}

    public EmpleadoDto(String empId, String nombres, String apellidos, String cargo,
                       LocalDate fechaContratacion, String empresa, Long departamentoId,
                       String departamentoNombre, String departamentoRuta, Boolean activo, Boolean desconocido) {
        this.empId = empId;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.nombreCompleto = (apellidos != null && !apellidos.isEmpty() ? apellidos + " " : "") + (nombres != null ? nombres : "");
        this.cargo = cargo;
        this.fechaContratacion = fechaContratacion;
        this.empresa = empresa;
        this.departamentoId = departamentoId;
        this.departamentoNombre = departamentoNombre;
        this.departamentoRuta = departamentoRuta;
        this.activo = activo;
        this.desconocido = desconocido;
    }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public LocalDate getFechaContratacion() { return fechaContratacion; }
    public void setFechaContratacion(LocalDate fechaContratacion) { this.fechaContratacion = fechaContratacion; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public Long getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(Long departamentoId) { this.departamentoId = departamentoId; }

    public String getDepartamentoNombre() { return departamentoNombre; }
    public void setDepartamentoNombre(String departamentoNombre) { this.departamentoNombre = departamentoNombre; }

    public String getDepartamentoRuta() { return departamentoRuta; }
    public void setDepartamentoRuta(String departamentoRuta) { this.departamentoRuta = departamentoRuta; }

    public java.math.BigDecimal getSueldoMensual() { return sueldoMensual; }
    public void setSueldoMensual(java.math.BigDecimal sueldoMensual) { this.sueldoMensual = sueldoMensual; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public Boolean getDesconocido() { return desconocido; }
    public void setDesconocido(Boolean desconocido) { this.desconocido = desconocido; }
}

