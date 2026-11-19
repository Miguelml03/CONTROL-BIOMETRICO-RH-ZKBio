package pe.gob.prorridre.asistencia.domain.repository;

import pe.gob.prorridre.asistencia.domain.entity.Marcacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de marcaciones crudas.
 *
 * <p>Extiende {@link JpaRepository} para operaciones CRUD estándar;
 * añade consultas específicas del negocio con JPQL.
 */
@Repository
public interface MarcacionRepository extends JpaRepository<Marcacion, Long> {

    /**
     * Devuelve el {@code punchTime} más reciente almacenado en la BD.
     *
     * <p>El lector programado usa este valor para construir el filtro
     * {@code start_time} de la próxima consulta a ZKBio Time y así
     * traer SOLO marcaciones nuevas (nunca recorre el histórico completo).
     *
     * @return el punch_time más reciente, o {@link Optional#empty()} si la BD está vacía
     */
    @Query("SELECT MAX(m.punchTime) FROM Marcacion m")
    Optional<LocalDateTime> findMaxPunchTime();

    /**
     * Últimas 50 marcaciones ordenadas por punch_time descendente.
     * Usadas en la página principal (Fase 1).
     *
     * @return lista de hasta 50 marcaciones
     */
    List<Marcacion> findTop50ByOrderByPunchTimeDesc();

    /**
     * Todas las marcaciones de un empleado en un rango de punch_time.
     * Se usará en el motor de reglas (Fase 2) para analizar la jornada diaria.
     *
     * @param empCode código del empleado
     * @param desde   inicio del rango (inclusive)
     * @param hasta   fin del rango (inclusive)
     * @return lista ordenada cronológicamente
     */
    List<Marcacion> findByEmpCodeAndPunchTimeBetweenOrderByPunchTimeAsc(
            String empCode, LocalDateTime desde, LocalDateTime hasta);

    /**
     * Cuenta cuántas marcaciones existen para un punch_time exacto y emp_code.
     * Usado para el control de antirrebote (Fase 2).
     *
     * @param empCode   código del empleado
     * @param punchTime momento exacto de la marcación
     * @return número de registros coincidentes (debería ser 0 o 1)
     */
    long countByEmpCodeAndPunchTime(String empCode, LocalDateTime punchTime);

    /**
     * Todos los códigos de empleado distintos que tienen marcaciones en el rango dado.
     * Útil para construir el panel de presentes (Fase 3).
     *
     * @param desde inicio del rango
     * @param hasta fin del rango
     * @return lista de emp_codes únicos
     */
    @Query("SELECT DISTINCT m.empCode FROM Marcacion m " +
           "WHERE m.punchTime BETWEEN :desde AND :hasta")
    List<String> findEmpCodesDistintosPorRango(
            @org.springframework.data.repository.query.Param("desde") LocalDateTime desde,
            @org.springframework.data.repository.query.Param("hasta") LocalDateTime hasta);

    List<Marcacion> findAllByOrderByEmpCodeAscPunchTimeAsc();

    List<Marcacion> findByPunchTimeBetweenOrderByEmpCodeAscPunchTimeAsc(
            LocalDateTime desde, LocalDateTime hasta);
}
