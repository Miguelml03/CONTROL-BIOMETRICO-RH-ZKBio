package pe.gob.prorridre.asistencia.dto;

import pe.gob.prorridre.asistencia.security.Rol;

public class UsuarioDto {
    private Long id;
    private String username;
    private String password; // Solo para crear/cambiar contraseña
    private String nombreCompleto;
    private Rol rol;
    private Boolean activo;

    public UsuarioDto() {}

    public UsuarioDto(Long id, String username, String nombreCompleto, Rol rol, Boolean activo) {
        this.id = id;
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = activo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}

