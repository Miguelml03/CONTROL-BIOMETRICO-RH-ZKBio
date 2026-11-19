package pe.gob.prorridre.asistencia.dto;

public class ImportResultDto {

    private boolean exitoso;
    private String mensaje;
    private int totalProcesados;
    private int registrosInsertados;
    private int registrosActualizados;
    private int errores;

    public ImportResultDto() {}

    public ImportResultDto(boolean exitoso, String mensaje, int totalProcesados, int registrosInsertados, int registrosActualizados, int errores) {
        this.exitoso = exitoso;
        this.mensaje = mensaje;
        this.totalProcesados = totalProcesados;
        this.registrosInsertados = registrosInsertados;
        this.registrosActualizados = registrosActualizados;
        this.errores = errores;
    }

    public boolean isExitoso() { return exitoso; }
    public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public int getTotalProcesados() { return totalProcesados; }
    public void setTotalProcesados(int totalProcesados) { this.totalProcesados = totalProcesados; }

    public int getRegistrosInsertados() { return registrosInsertados; }
    public void setRegistrosInsertados(int registrosInsertados) { this.registrosInsertados = registrosInsertados; }

    public int getRegistrosActualizados() { return registrosActualizados; }
    public void setRegistrosActualizados(int registrosActualizados) { this.registrosActualizados = registrosActualizados; }

    public int getErrores() { return errores; }
    public void setErrores(int errores) { this.errores = errores; }
}

