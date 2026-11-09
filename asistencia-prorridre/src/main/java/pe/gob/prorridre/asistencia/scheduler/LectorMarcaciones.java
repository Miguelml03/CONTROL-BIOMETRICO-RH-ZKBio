package pe.gob.prorridre.asistencia.scheduler;

import pe.gob.prorridre.asistencia.config.AppConfig;
import pe.gob.prorridre.asistencia.service.MarcacionService;
import pe.gob.prorridre.asistencia.zkbio.ZkBioClient;
import pe.gob.prorridre.asistencia.zkbio.dto.TransaccionDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lector programado de marcaciones biométricas.
 *
 * <p>Se ejecuta cada {@code app.lector.periodo-ms} milisegundos (por defecto 20 s).
 * En cada ciclo:
 * <ol>
 *   <li>Calcula el rango de fechas a consultar (desde el último punchTime − 1 día).</li>
 *   <li>Llama a {@link ZkBioClient} para obtener las marcaciones nuevas (paginadas).</li>
 *   <li>Llama a {@link MarcacionService} para persistir solo las que no existen aún.</li>
 *   <li>Registra en el log cuántas se insertaron.</li>
 * </ol>
 *
 * <p><strong>SOLO LEE</strong> de ZKBio Time; no escribe, modifica ni elimina nada allí.
 *
 * <p>Si el reloj no responde o devuelve un error, el ciclo termina con una advertencia
 * y el siguiente ciclo lo reintentará automáticamente.
 */
@Component
public class LectorMarcaciones {

    private static final Logger log = LoggerFactory.getLogger(LectorMarcaciones.class);

    private final ZkBioClient zkBioClient;
    private final MarcacionService marcacionService;
    private final AppConfig config;

    public LectorMarcaciones(ZkBioClient zkBioClient,
                             MarcacionService marcacionService,
                             AppConfig config) {
        this.zkBioClient      = zkBioClient;
        this.marcacionService = marcacionService;
        this.config           = config;
    }

    /**
     * Ciclo principal de lectura.
     *
     * <p>{@code fixedDelayString} respeta {@code app.lector.periodo-ms} de
     * {@code application.properties}; el retraso se mide desde que termina
     * el ciclo anterior (no desde que empieza), evitando solapamiento.
     *
     * <p>La primera ejecución ocurre también tras el retardo inicial
     * ({@code initialDelay}) para dar tiempo a que la BD y el contexto
     * Spring estén completamente listos.
     */
    @Scheduled(
        fixedDelayString  = "${app.lector.periodo-ms:20000}",
        initialDelayString = "${app.lector.periodo-ms:20000}"
    )
    public void leer() {
        log.debug("[Lector] Iniciando ciclo de lectura de marcaciones.");
        long inicio = System.currentTimeMillis();

        try {
            // 1. Rango de fechas: desde el último punchTime guardado − 1 día hasta ahora
            LocalDateTime desde = marcacionService.calcularDesde();
            LocalDateTime hasta = LocalDateTime.now();

            log.info("[Lector] Consultando marcaciones en rango [{} → {}]", desde, hasta);

            // 2. Obtener marcaciones de ZKBio Time (paginado automáticamente)
            List<TransaccionDto> transacciones = zkBioClient.obtenerMarcaciones(desde, hasta);

            if (transacciones.isEmpty()) {
                log.info("[Lector] Sin marcaciones nuevas en este ciclo.");
                return;
            }

            // 3. Persistir solo las nuevas (duplicados ignorados internamente)
            int insertadas = marcacionService.guardarNuevas(transacciones);

            long duracion = System.currentTimeMillis() - inicio;
            log.info("[Lector] Ciclo completado: {} recibidas, {} nuevas insertadas. ({} ms)",
                     transacciones.size(), insertadas, duracion);

        } catch (Exception ex) {
            // No se deja propagar: Spring no desactivaría el @Scheduled por una excepción,
            // pero es mejor registrar el error claramente para diagnóstico.
            log.error("[Lector] Error en el ciclo de lectura: {}. " +
                      "El siguiente ciclo se reintentará automáticamente.", ex.getMessage(), ex);
        }
    }
}
