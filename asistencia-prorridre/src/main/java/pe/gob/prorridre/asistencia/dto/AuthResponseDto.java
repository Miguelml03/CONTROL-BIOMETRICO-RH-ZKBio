package pe.gob.prorridre.asistencia.dto;

public class AuthResponseDto {
    private boolean exitoso;
    private String mensaje;
    private String token;
    private String username;
    private String nombreCompleto;
    private String rol;

    public AuthResponseDto() {}

    public AuthResponseDto(boolean exitoso, String mensaje, String token, String username, String nombreCompleto, String rol) {
        this.exitoso = exitoso;
        this.mensaje = mensaje;
        this.token = token;
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
    }

    public static AuthResponseDto error(String mensaje) {
        return new AuthResponseDto(false, mensaje, null, null, null, null);
    }

    public boolean isExitoso() { return exitoso; }
    public void setExitoso(boolean exitoso) { this.exitoso = exitoso; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}

