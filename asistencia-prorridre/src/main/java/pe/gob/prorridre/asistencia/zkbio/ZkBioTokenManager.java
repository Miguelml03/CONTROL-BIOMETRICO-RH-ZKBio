package pe.gob.prorridre.asistencia.zkbio;

import pe.gob.prorridre.asistencia.config.AppConfig;
import pe.gob.prorridre.asistencia.zkbio.dto.LoginRequest;
import pe.gob.prorridre.asistencia.zkbio.dto.LoginResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Gestor del token JWT de ZKBio Time.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Realizar el login inicial y obtener el token.</li>
 *   <li>Guardar el token en memoria (no en base de datos).</li>
 *   <li>Renovar automáticamente el token cuando el servidor devuelva 401 o 403.</li>
 *   <li>Proporcionar los {@link HttpHeaders} listos para usar en cada petición.</li>
 * </ul>
 *
 * <p><strong>Seguridad:</strong> el token NUNCA se imprime en los logs;
 * solo se registra "token obtenido" o "token renovado".
 */
@Component
public class ZkBioTokenManager {

    private static final Logger log = LoggerFactory.getLogger(ZkBioTokenManager.class);

    /** Endpoint de autenticación JWT de ZKBio Time. */
    private static final String LOGIN_PATH = "/jwt-api-token-auth/";

    private final AppConfig config;
    private final RestTemplate restTemplate;

    /** Token activo en memoria. Null si no se ha hecho login todavía. */
    private volatile String tokenActual = null;

    /** Momento en que se obtuvo el token (para futura expiración por tiempo). */
    private volatile Instant tokenObtenidoEn = null;

    public ZkBioTokenManager(AppConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    /**
     * Devuelve los headers HTTP con el token JWT vigente.
     *
     * <p>Si no hay token, hace login automáticamente.
     *
     * @return headers con {@code Content-Type: application/json} y {@code Authorization: JWT <token>}
     * @throws RuntimeException si el login falla
     */
    public HttpHeaders obtenerHeaders() {
        if (tokenActual == null) {
            login();
        }
        return construirHeaders(tokenActual);
    }

    /**
     * Invalida el token actual y fuerza un nuevo login.
     *
     * <p>Debe llamarse cuando el servidor devuelva 401 o 403.
     *
     * @return headers renovados listos para reintentar la petición original
     * @throws RuntimeException si el re-login falla
     */
    public HttpHeaders renovarToken() {
        log.info("[ZKBio] Token inválido o expirado – iniciando renovación de sesión");
        tokenActual = null;
        login();
        return construirHeaders(tokenActual);
    }

    /**
     * Indica si hay un token activo en memoria.
     *
     * @return true si el token está presente
     */
    public boolean tieneToken() {
        return tokenActual != null;
    }

    /* ------------------------------------------------------------------
     * Métodos privados
     * ---------------------------------------------------------------- */

    /**
     * Realiza el POST de autenticación a ZKBio Time y guarda el token.
     *
     * <p>Usa las credenciales de las variables de entorno (validadas en {@link AppConfig}).
     * NUNCA se registran las credenciales ni el token en los logs.
     *
     * @throws RuntimeException si el servidor rechaza las credenciales o no responde
     */
    private synchronized void login() {
        // Doble-check: otro hilo pudo haber hecho login mientras esperábamos el lock
        if (tokenActual != null) {
            return;
        }

        String url = config.getZkbioUrl() + LOGIN_PATH;
        log.info("[ZKBio] Autenticando en {}", url);

        LoginRequest peticion = new LoginRequest(config.getZkbioUsuario(), config.getZkbioClave());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<LoginResponse> respuesta = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(peticion, headers),
                LoginResponse.class
            );

            LoginResponse cuerpo = respuesta.getBody();
            if (cuerpo == null || cuerpo.getToken() == null || cuerpo.getToken().isBlank()) {
                throw new RuntimeException("[ZKBio] El servidor respondió pero no devolvió token. " +
                                           "Verifique las credenciales ZK_USER / ZK_PASS.");
            }

            tokenActual    = cuerpo.getToken();
            tokenObtenidoEn = Instant.now();
            log.info("[ZKBio] Sesión iniciada correctamente (token obtenido)");

        } catch (HttpClientErrorException ex) {
            throw new RuntimeException(
                "[ZKBio] Error de autenticación HTTP " + ex.getStatusCode() +
                " – verifique las credenciales ZK_USER / ZK_PASS y que el servidor esté accesible.", ex);
        }
    }

    /**
     * Construye los headers HTTP para una petición autenticada.
     *
     * @param token el JWT activo
     * @return headers con Authorization y Content-Type
     */
    private HttpHeaders construirHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "JWT " + token);
        return headers;
    }
}
