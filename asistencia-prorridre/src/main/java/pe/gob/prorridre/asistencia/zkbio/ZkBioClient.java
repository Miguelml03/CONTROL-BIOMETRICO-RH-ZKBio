package pe.gob.prorridre.asistencia.zkbio;

import pe.gob.prorridre.asistencia.config.AppConfig;
import pe.gob.prorridre.asistencia.zkbio.dto.PaginaTransacciones;
import pe.gob.prorridre.asistencia.zkbio.dto.TransaccionDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente HTTP de <strong>SOLO LECTURA</strong> para ZKBio Time.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Consultar el endpoint {@code GET /iclock/api/transactions/} con filtro de fecha.</li>
 *   <li>Paginar automáticamente manteniendo los filtros de fecha en cada página.</li>
 *   <li>Manejar errores 401/403 renovando el token JWT y reintentando UNA vez.</li>
 *   <li>No modificar, borrar ni escribir nada en ZKBio Time.</li>
 * </ul>
 *
 * <p>El formato de fecha que ZKBio Time espera en los parámetros es:
 * {@code YYYY-MM-DD HH:mm:ss} (codificado en la URL como {@code YYYY-MM-DD+HH%3Amm%3Ass}).
 */
@Component
public class ZkBioClient {

    private static final Logger log = LoggerFactory.getLogger(ZkBioClient.class);

    /** Endpoint de marcaciones de ZKBio Time. */
    private static final String TRANSACCIONES_PATH = "/iclock/api/transactions/";

    /** Formato de fecha/hora que espera ZKBio Time en los parámetros de consulta. */
    private static final DateTimeFormatter FORMATO_ZKBIO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppConfig config;
    private final ZkBioTokenManager tokenManager;
    private final RestTemplate restTemplate;

    public ZkBioClient(AppConfig config,
                       ZkBioTokenManager tokenManager,
                       RestTemplate restTemplate) {
        this.config       = config;
        this.tokenManager = tokenManager;
        this.restTemplate = restTemplate;
    }

    /**
     * Obtiene TODAS las marcaciones entre {@code desde} y {@code hasta},
     * paginando automáticamente.
     *
     * <p>El lector programado llama a este método con:
     * <ul>
     *   <li>{@code desde} = último punchTime guardado − 1 día (ventana de seguridad)</li>
     *   <li>{@code hasta} = ahora (en hora local America/Lima)</li>
     * </ul>
     *
     * @param desde inicio del filtro start_time (inclusive), hora local
     * @param hasta fin del filtro end_time (inclusive), hora local
     * @return lista completa de marcaciones en el rango, nunca null
     */
    public List<TransaccionDto> obtenerMarcaciones(LocalDateTime desde, LocalDateTime hasta) {
        List<TransaccionDto> resultado = new ArrayList<>();

        int pagina = 1;
        int paginasConsultadas = 0;
        String urlActual = construirUrl(desde, hasta, pagina);

        log.info("[ZKBio] Consultando marcaciones desde={} hasta={}", desde, hasta);

        while (true) {
            PaginaTransacciones pagResult = consultarPagina(urlActual, pagina);
            paginasConsultadas++;
            if (pagResult == null || pagResult.getData() == null) {
                log.warn("[ZKBio] Página {} devolvió respuesta vacía; deteniendo paginación.", pagina);
                break;
            }

            resultado.addAll(pagResult.getData());
            log.debug("[ZKBio] Página {} obtenida: {} registros (total acumulado: {})",
                      pagina, pagResult.getData().size(), resultado.size());

            if (!pagResult.hayMasPaginas()) {
                break;
            }

            // Reconstruir la URL evita alterar la codificación de fechas del enlace "next".
            pagina++;
            urlActual = construirUrl(desde, hasta, pagina);
        }

        log.info("[ZKBio] Consulta completada: {} marcaciones obtenidas en {} página(s).",
                 resultado.size(), paginasConsultadas);
        return resultado;
    }

    /* ------------------------------------------------------------------
     * Métodos privados
     * ---------------------------------------------------------------- */

    /**
     * Consulta una página de marcaciones con renovación de token automática.
     *
     * <p>Si el servidor devuelve 401 o 403, renueva el token y reintenta
     * la petición UNA SOLA VEZ.
     *
     * @param url    URL completa (puede ser la inicial o el campo "next" de ZKBio)
     * @param pagina número de página (solo para logs)
     * @return la página deserializada, o null si el servidor responde vacío
     * @throws RuntimeException si el error persiste tras la renovación del token
     */
    private PaginaTransacciones consultarPagina(String url, int pagina) {
        try {
            return ejecutarGet(url, tokenManager.obtenerHeaders());

        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();

            if (status.value() == 401 || status.value() == 403) {
                log.warn("[ZKBio] Respuesta {} en página {}. Renovando token y reintentando…",
                         status.value(), pagina);
                // Renovar token y reintentar UNA VEZ
                return ejecutarGet(url, tokenManager.renovarToken());
            }

            throw new RuntimeException(
                "[ZKBio] Error HTTP " + status.value() + " al consultar marcaciones (página " + pagina + ")", ex);
        }
    }

    /**
     * Ejecuta el GET HTTP y deserializa la respuesta.
     *
     * @param url     URL completa del endpoint
     * @param headers headers con el token JWT vigente
     * @return respuesta deserializada
     */
    private PaginaTransacciones ejecutarGet(String url, HttpHeaders headers) {
        ResponseEntity<PaginaTransacciones> respuesta = restTemplate.exchange(
            url,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            PaginaTransacciones.class
        );
        return respuesta.getBody();
    }

    /**
     * Construye la URL de una página de marcaciones.
     *
     * <p>ZKBio Time requiere que {@code start_time} y {@code end_time} estén
     * en el formato {@code yyyy-MM-dd HH:mm:ss}.
     * Spring's {@link UriComponentsBuilder} codifica el espacio como {@code +}
     * y los dos puntos, que es lo que acepta ZKBio Time.
     *
     * @param desde inicio del rango
     * @param hasta fin del rango
     * @param pagina número de página solicitado
     * @return URL lista para hacer GET
     */
    private String construirUrl(LocalDateTime desde, LocalDateTime hasta, int pagina) {
        return UriComponentsBuilder
            .fromHttpUrl(config.getZkbioUrl() + TRANSACCIONES_PATH)
            .queryParam("page", pagina)
            .queryParam("page_size", config.getPageSize())
            .queryParam("start_time", desde.format(FORMATO_ZKBIO))
            .queryParam("end_time", hasta.format(FORMATO_ZKBIO))
            .build()
            .toUriString();
    }
}
