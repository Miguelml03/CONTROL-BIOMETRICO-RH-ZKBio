package pe.gob.prorridre.asistencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Configuración central de la aplicación.
 *
 * <p>Lee los parámetros de conexión desde {@code application.properties}
 * y valida que las variables de entorno de credenciales estén presentes
 * antes de que arranque cualquier otro componente.
 *
 * <p>NUNCA se imprimen ni se registran las credenciales en los logs.
 */
@Configuration
public class AppConfig {

    /* -----------------------------------------------------------------------
     * Parámetros de ZKBio Time – inyectados desde application.properties
     * --------------------------------------------------------------------- */

    /** URL base del servidor ZKBio Time (sin barra final). */
    @Value("${zkbio.url}")
    private String zkbioUrl;

    /**
     * Nombre de usuario de ZKBio Time.
     * Se resuelve desde la variable de entorno {@code ZK_USER}.
     */
    @Value("${zkbio.usuario}")
    private String zkbioUsuario;

    /**
     * Contraseña de ZKBio Time.
     * Se resuelve desde la variable de entorno {@code ZK_PASS}.
     */
    @Value("${zkbio.clave}")
    private String zkbioClave;

    /** Tiempo de espera para peticiones HTTP al reloj (ms). */
    @Value("${zkbio.timeout-ms:15000}")
    private int timeoutMs;

    /** Tamaño de página al paginar marcaciones. */
    @Value("${zkbio.page-size:1000}")
    private int pageSize;

    /* -----------------------------------------------------------------------
     * Parámetros de la aplicación
     * --------------------------------------------------------------------- */

    /** Zona horaria para interpretar los punch_time (p. ej. America/Lima). */
    @Value("${app.zona-horaria:America/Lima}")
    private String zonaHoraria;

    /** Días hacia atrás a consultar en la primera ejecución del lector. */
    @Value("${app.lector.dias-atras-inicial:1}")
    private int diasAtrasInicial;

    /* -----------------------------------------------------------------------
     * Validación al arrancar
     * --------------------------------------------------------------------- */

    /**
     * Verifica que las credenciales existan y no estén vacías.
     * Si faltan, lanza una excepción con un mensaje claro ANTES de que
     * Spring arranque el resto del contexto.
     */
    @PostConstruct
    public void validarCredenciales() {
        if (!StringUtils.hasText(zkbioUsuario)) {
            throw new IllegalStateException(
                "[PRORRIDRE] La variable de entorno ZK_USER no está definida o está vacía. " +
                "Defínela antes de ejecutar la aplicación.");
        }
        if (!StringUtils.hasText(zkbioClave)) {
            throw new IllegalStateException(
                "[PRORRIDRE] La variable de entorno ZK_PASS no está definida o está vacía. " +
                "Defínela antes de ejecutar la aplicación.");
        }
    }

    /* -----------------------------------------------------------------------
     * Beans
     * --------------------------------------------------------------------- */

    /**
     * {@link RestTemplate} compartido para todas las peticiones HTTP a ZKBio Time.
     * En fases siguientes se puede reemplazar por WebClient (reactivo).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /* -----------------------------------------------------------------------
     * Getters (sin Lombok para que no haya confusión con las anotaciones)
     * --------------------------------------------------------------------- */

    public String getZkbioUrl() { return zkbioUrl; }
    public String getZkbioUsuario() { return zkbioUsuario; }
    public String getZkbioClave() { return zkbioClave; }
    public int getTimeoutMs() { return timeoutMs; }
    public int getPageSize() { return pageSize; }
    public String getZonaHoraria() { return zonaHoraria; }
    public int getDiasAtrasInicial() { return diasAtrasInicial; }
}
