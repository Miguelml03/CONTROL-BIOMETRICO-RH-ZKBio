package pe.gob.prorridre.asistencia.web;

import pe.gob.prorridre.asistencia.domain.entity.EstadoMarcacion;
import pe.gob.prorridre.asistencia.domain.entity.Marcacion;
import pe.gob.prorridre.asistencia.domain.repository.EstadoMarcacionRepository;
import pe.gob.prorridre.asistencia.service.MarcacionService;
import pe.gob.prorridre.asistencia.web.dto.MarcacionResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador REST interno de la aplicación.
 *
 * <p>Expone los endpoints que consume la interfaz web en tiempo real.
 * En Fases 2, 3 y 4 se añadirán más endpoints para empleados, panel en vivo
 * y reportes.
 *
 * <p>Todos los endpoints son de <strong>solo lectura</strong>;
 * no modifican datos en ZKBio Time.
 */
@RestController
@RequestMapping("/api")
public class MarcacionController {

    private final MarcacionService marcacionService;
    private final EstadoMarcacionRepository estadoRepo;

    public MarcacionController(MarcacionService marcacionService,
                                EstadoMarcacionRepository estadoRepo) {
        this.marcacionService = marcacionService;
        this.estadoRepo       = estadoRepo;
    }

    /**
     * Devuelve las últimas 50 marcaciones con el significado real del estado.
     *
     * <p>La interfaz web consulta este endpoint cada 10 segundos para
     * actualizarse sin recargar la página.
     *
     * <p>GET /api/marcaciones/ultimas
     *
     * @return lista de hasta 50 marcaciones como JSON
     */
    @GetMapping("/marcaciones/ultimas")
    public ResponseEntity<List<MarcacionResponse>> ultimas() {

        // Cargar el mapa de estados para no hacer N consultas a BD
        Map<String, String> mapaEstados = construirMapaEstados();

        List<MarcacionResponse> resultado = marcacionService.obtenerUltimas50().stream()
            .map(m -> new MarcacionResponse(
                m.getId(),
                m.getEmpCode(),
                m.getFirstName(),
                m.getLastName(),
                m.getPunchTime(),
                m.getPunchState(),
                mapaEstados.getOrDefault(m.getPunchState(),
                    "Desconocido (" + m.getPunchState() + ")"),
                m.getVerifyType(),
                m.getTerminalSn()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(resultado);
    }

    /**
     * Devuelve la configuración de estados de marcación.
     *
     * <p>Usada en Fase 4 para la pantalla de configuración;
     * también sirve para que la interfaz web muestre la leyenda de colores.
     *
     * <p>GET /api/estados
     *
     * @return lista de todos los estados activos
     */
    @GetMapping("/estados")
    public ResponseEntity<List<EstadoMarcacion>> estados() {
        return ResponseEntity.ok(
            estadoRepo.findByActivoTrueOrderByOrdenSecuenciaAsc()
        );
    }

    /**
     * Endpoint de salud/información básica del sistema.
     *
     * <p>GET /api/info
     *
     * @return mapa con estado del sistema y hora del servidor
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("sistema", "PRORRIDRE – Control de Asistencia");
        datos.put("version", "1.0.0-FASE1");
        datos.put("horaServidor", LocalDateTime.now().toString());
        datos.put("estado", "ACTIVO");
        return ResponseEntity.ok(datos);
    }

    /* ------------------------------------------------------------------
     * Métodos auxiliares
     * ---------------------------------------------------------------- */

    /**
     * Construye un mapa {@code punchState → significadoReal} leyendo
     * todos los estados activos de la BD (una sola consulta).
     *
     * @return mapa de estados activos
     */
    private Map<String, String> construirMapaEstados() {
        Map<String, String> mapa = new HashMap<>();
        for (EstadoMarcacion e : estadoRepo.findByActivoTrueOrderByOrdenSecuenciaAsc()) {
            mapa.put(e.getPunchState(), e.getSignificadoReal());
        }
        return mapa;
    }
}
