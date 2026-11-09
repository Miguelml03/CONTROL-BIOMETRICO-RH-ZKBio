package pe.gob.prorridre.asistencia.zkbio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de petición de login a ZKBio Time.
 *
 * <p>Se envía como JSON en el cuerpo de:
 * {@code POST /jwt-api-token-auth/}
 *
 * <p>NUNCA se loguea este objeto; los campos son sensibles.
 */
public class LoginRequest {

    @JsonProperty("username")
    private final String username;

    @JsonProperty("password")
    private final String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }

    /**
     * toString intencionalmente omite la contraseña para que no aparezca
     * si se loguea este objeto por error.
     */
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "', password='[OCULTO]'}";
    }
}
