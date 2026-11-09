package pe.gob.prorridre.asistencia.zkbio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de respuesta del endpoint de autenticación de ZKBio Time.
 *
 * <p>Estructura real de la respuesta:
 * <pre>
 * {"token": "eyJ0eXAiOiJKV1Qi..."}
 * </pre>
 *
 * <p>El token se incluye como {@code Authorization: JWT <token>}
 * en todos los siguientes pedidos.
 */
public class LoginResponse {

    @JsonProperty("token")
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    /** toString omite el valor real del token por seguridad. */
    @Override
    public String toString() {
        return "LoginResponse{token='[PRESENTE=" + (token != null && !token.isBlank()) + "]'}";
    }
}
