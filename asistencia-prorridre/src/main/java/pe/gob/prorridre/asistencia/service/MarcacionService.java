package pe.gob.prorridre.asistencia.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.gob.prorridre.asistencia.config.AppConfig;
import pe.gob.prorridre.asistencia.domain.entity.Marcacion;
import pe.gob.prorridre.asistencia.domain.repository.MarcacionRepository;
import pe.gob.prorridre.asistencia.zkbio.dto.TransaccionDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Servicio de marcaciones crudas.
 *
 * <p>Convierte los DTOs de ZKBio Time a entidades {@link Marcacion}
 * y las persiste en la base de datos, ignorando duplicados de forma segura.
 *
 * <p>También calcula el rango de fechas a consultar en cada ciclo del lector.
 */
@Service
public class MarcacionService {

    private static final Logger log = LoggerFactory.getLogger(MarcacionService.class);

    /** Formato de punch_time que devuelve ZKBio Time: "2025-07-19 12:10:05" */
    private static final DateTimeFormatter FMT_ZKBIO =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MarcacionRepository repo;
    private final AppConfig config;
    private final ObjectMapper mapper;

    public MarcacionService(MarcacionRepository repo,
                            AppConfig config,
                            ObjectMapper mapper) {
        this.repo   = repo;
        this.config = config;
        this.mapper = mapper;
    }

    /**
     * Devuelve el instante de inicio para la próxima consulta a ZKBio Time.
     *
     * <p>Estrategia: se toma el {@code punchTime} máximo guardado en la BD
     * y se resta 1 día como ventana de seguridad (absorbe marcaciones que
     * el reloj sube con retraso).  Si la BD está vacía, se retrocede
     * {@link AppConfig#getDiasAtrasInicial()} días desde ahora.
     *
     * <p>Gracias a este mecanismo NUNCA se recorre el histórico completo
     * (~160 000 registros); solo se trae lo nuevo.
     *
     * @return inicio del rango para el filtro {@code start_time}
     */
    public LocalDateTime calcularDesde() {
        return repo.findMaxPunchTime()
            .map(maximo -> maximo.minusDays(1))
            .orElse(LocalDateTime.now().minusDays(config.getDiasAtrasInicial()));
    }

    /**
     * Persiste las marcaciones recibidas de ZKBio Time, ignorando duplicados.
     *
     * <p>La duplicación se evita a nivel de clave primaria ({@code id} de ZKBio).
     * JPA lanzaría una excepción si se intentara insertar un id que ya existe,
     * por eso se hace un {@code existsById} antes de guardar.
     *
     * @param transacciones lista de DTOs crudos de ZKBio Time
     * @return número de marcaciones realmente insertadas (ignorando duplicados)
     */
    @Transactional
    public int guardarNuevas(List<TransaccionDto> transacciones) {
        if (transacciones == null || transacciones.isEmpty()) {
            return 0;
        }

        int insertadas = 0;
        int duplicadas = 0;

        for (TransaccionDto dto : transacciones) {
            if (dto.getId() == null) {
                log.warn("[Servicio] Marcación sin ID recibida de ZKBio, se ignora: {}", dto);
                continue;
            }

            // Control de duplicados: si el ID ya existe en BD, se salta
            if (repo.existsById(dto.getId())) {
                duplicadas++;
                continue;
            }

            Marcacion entidad = convertir(dto);
            if (entidad != null) {
                repo.save(entidad);
                insertadas++;
            }
        }

        if (duplicadas > 0) {
            log.debug("[Servicio] {} duplicados ignorados en este ciclo.", duplicadas);
        }
        return insertadas;
    }

    /**
     * Devuelve las últimas 50 marcaciones ordenadas de la más reciente a la más antigua.
     * Usadas por el controlador REST para la página principal (Fase 1).
     *
     * @return lista de hasta 50 marcaciones
     */
    @Transactional(readOnly = true)
    public List<Marcacion> obtenerUltimas50() {
        List<Marcacion> marcaciones = repo.findTop50ByOrderByPunchTimeDesc();
        for (Marcacion marcacion : marcaciones) {
            completarNombreDesdeJson(marcacion);
        }
        return marcaciones;
    }

    /* ------------------------------------------------------------------
     * Conversión DTO → Entidad
     * ---------------------------------------------------------------- */

    /**
     * Convierte un {@link TransaccionDto} en una entidad {@link Marcacion}.
     *
     * <p>Si el {@code punchTime} no puede parsearse, la marcación se descarta
     * con una advertencia en el log (no debe propagarse una excepción que
     * interrumpa el lote entero).
     *
     * @param dto DTO recibido de ZKBio Time
     * @return entidad lista para persistir, o {@code null} si hay error de parseo
     */
    private Marcacion convertir(TransaccionDto dto) {
        Marcacion m = new Marcacion();
        m.setId(dto.getId());
        m.setEmpCode(dto.getEmpCode());
        m.setFirstName(dto.getFirstName());
        m.setLastName(dto.getLastName());
        m.setPunchState(dto.getPunchState());
        m.setVerifyType(dto.getVerifyType());
        m.setTerminalSn(dto.getTerminalSn());

        // --- punchTime (obligatorio para todos los cálculos) ---
        try {
            m.setPunchTime(LocalDateTime.parse(dto.getPunchTime(), FMT_ZKBIO));
        } catch (DateTimeParseException | NullPointerException ex) {
            log.warn("[Servicio] punch_time inválido en marcación id={}: '{}'. Marcación descartada.",
                     dto.getId(), dto.getPunchTime());
            return null;
        }

        // --- uploadTime (opcional; puede venir vacío o nulo) ---
        if (dto.getUploadTime() != null && !dto.getUploadTime().isBlank()) {
            try {
                m.setUploadTime(LocalDateTime.parse(dto.getUploadTime(), FMT_ZKBIO));
            } catch (DateTimeParseException ex) {
                log.debug("[Servicio] upload_time inválido en marcación id={}: '{}'. Se almacena como null.",
                          dto.getId(), dto.getUploadTime());
            }
        }

        // --- rawJson: guardar el DTO completo para auditoría ---
        try {
            m.setRawJson(mapper.writeValueAsString(dto));
        } catch (JsonProcessingException ex) {
            log.warn("[Servicio] No se pudo serializar rawJson para marcación id={}", dto.getId());
        }

        return m;
    }

    private void completarNombreDesdeJson(Marcacion marcacion) {
        if (marcacion.getRawJson() == null
                || (tieneTexto(marcacion.getFirstName()) && tieneTexto(marcacion.getLastName()))) {
            return;
        }

        try {
            TransaccionDto dto = mapper.readValue(marcacion.getRawJson(), TransaccionDto.class);
            if (!tieneTexto(marcacion.getFirstName())) {
                marcacion.setFirstName(dto.getFirstName());
            }
            if (!tieneTexto(marcacion.getLastName())) {
                marcacion.setLastName(dto.getLastName());
            }
        } catch (DatabindException ex) {
            log.warn("[Servicio] raw_json inválido para marcación id={}; no se pudo recuperar el nombre.",
                     marcacion.getId());
        } catch (JsonProcessingException ex) {
            log.warn("[Servicio] No se pudo leer raw_json para marcación id={}.", marcacion.getId());
        }
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
