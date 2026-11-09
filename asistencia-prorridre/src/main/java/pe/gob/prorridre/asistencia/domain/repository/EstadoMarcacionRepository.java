package pe.gob.prorridre.asistencia.domain.repository;

import pe.gob.prorridre.asistencia.domain.entity.EstadoMarcacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de la tabla de configuración de estados de marcación.
 *
 * <p>Los estados activos son los que se usan en la interfaz y en el motor de reglas.
 */
@Repository
public interface EstadoMarcacionRepository extends JpaRepository<EstadoMarcacion, String> {

    /**
     * Devuelve solo los estados marcados como activos,
     * ordenados por su posición en la secuencia de la jornada.
     *
     * @return estados activos en orden de secuencia
     */
    List<EstadoMarcacion> findByActivoTrueOrderByOrdenSecuenciaAsc();

    /**
     * Busca el estado correspondiente a un punch_state dado (solo si está activo).
     *
     * @param punchState código de estado (p. ej. "0", "1", "4", "5")
     * @return el estado, o empty si no está configurado o no está activo
     */
    Optional<EstadoMarcacion> findByPunchStateAndActivoTrue(String punchState);
}
