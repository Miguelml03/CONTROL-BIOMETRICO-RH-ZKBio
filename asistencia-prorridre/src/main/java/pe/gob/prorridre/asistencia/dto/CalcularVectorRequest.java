package pe.gob.prorridre.asistencia.dto;

public class CalcularVectorRequest {

    private String empId = "TEST-01";
    private String nombres = "DEMO";
    private String apellidos = "USUARIO";
    private String departamento = "ADMINISTRACION";
    private String horasCrudas;

    public CalcularVectorRequest() {}

    public CalcularVectorRequest(String horasCrudas) {
        this.horasCrudas = horasCrudas;
    }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }

    public String getHorasCrudas() { return horasCrudas; }
    public void setHorasCrudas(String horasCrudas) { this.horasCrudas = horasCrudas; }
}

