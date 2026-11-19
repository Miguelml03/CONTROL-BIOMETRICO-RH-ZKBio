package pe.gob.prorridre.asistencia.dto;

/**
 * Resultado de la sincronización de datos maestros (Departamentos y Empleados)
 * desde ZKBio Time 8.0.8 hacia la base de datos local.
 */
public class SincronizacionMaestrosResultDto {

    private boolean exitoso;
    private String mensaje;
    private int totalDepartamentos;
    private int departamentosInsertados;
    private int departamentosActualizados;
    private int totalEmpleados;
    private int empleadosInsertados;
    private int empleadosActualizados;
    private int empleadosDesconocidos;
    private long duracionMs;

    public SincronizacionMaestrosResultDto() {}

    public SincronizacionMaestrosResultDto(boolean exitoso, String mensaje) {
        this.exitoso = exitoso;
        this.mensaje = mensaje;
    }

    public boolean isExitoso() { return exitoso; }
    public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public int getTotalDepartamentos() { return totalDepartamentos; }
    public void setTotalDepartamentos(int totalDepartamentos) { this.totalDepartamentos = totalDepartamentos; }

    public int getDepartamentosInsertados() { return departamentosInsertados; }
    public void setDepartamentosInsertados(int departamentosInsertados) { this.departamentosInsertados = departamentosInsertados; }

    public int getDepartamentosActualizados() { return departamentosActualizados; }
    public void setDepartamentosActualizados(int departamentosActualizados) { this.departamentosActualizados = departamentosActualizados; }

    public int getTotalEmpleados() { return totalEmpleados; }
    public void setTotalEmpleados(int totalEmpleados) { this.totalEmpleados = totalEmpleados; }

    public int getEmpleadosInsertados() { return empleadosInsertados; }
    public void setEmpleadosInsertados(int empleadosInsertados) { this.empleadosInsertados = empleadosInsertados; }

    public int getEmpleadosActualizados() { return empleadosActualizados; }
    public void setEmpleadosActualizados(int empleadosActualizados) { this.empleadosActualizados = empleadosActualizados; }

    public int getEmpleadosDesconocidos() { return empleadosDesconocidos; }
    public void setEmpleadosDesconocidos(int empleadosDesconocidos) { this.empleadosDesconocidos = empleadosDesconocidos; }

    public long getDuracionMs() { return duracionMs; }
    public void setDuracionMs(long duracionMs) { this.duracionMs = duracionMs; }
}

