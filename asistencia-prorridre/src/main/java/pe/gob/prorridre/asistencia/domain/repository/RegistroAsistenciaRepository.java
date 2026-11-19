package pe.gob.prorridre.asistencia.domain.repository;

import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroAsistenciaRepository extends JpaRepository<RegistroAsistencia, Long> {

    Optional<RegistroAsistencia> findByEmpIdAndFecha(String empId, LocalDate fecha);

    List<RegistroAsistencia> findByFechaOrderByDepartamentoAscApellidosAsc(LocalDate fecha);

    List<RegistroAsistencia> findByFechaBetweenOrderByFechaDescDepartamentoAscApellidosAsc(LocalDate desde, LocalDate hasta);

    List<RegistroAsistencia> findByEmpIdOrderByFechaDesc(String empId);

    List<RegistroAsistencia> findByEmpIdAndFechaBetweenOrderByFechaAsc(String empId, LocalDate desde, LocalDate hasta);

    List<RegistroAsistencia> findByDepartamentoIgnoreCaseAndFechaOrderByApellidosAsc(String departamento, LocalDate fecha);

    List<RegistroAsistencia> findByFechaBetweenAndDepartamentoIgnoreCaseOrderByFechaAscApellidosAsc(LocalDate desde, LocalDate hasta, String departamento);

    List<RegistroAsistencia> findByDepartamentoIgnoreCaseOrderByFechaDescApellidosAsc(String departamento);

    List<RegistroAsistencia> findAllByOrderByFechaDescApellidosAsc();

    @Query("SELECT DISTINCT r.departamento FROM RegistroAsistencia r WHERE r.departamento IS NOT NULL AND TRIM(r.departamento) != '' ORDER BY r.departamento")
    List<String> findDistinctDepartamentos();

    @Query("SELECT DISTINCT r.fecha FROM RegistroAsistencia r ORDER BY r.fecha DESC")
    List<LocalDate> findDistinctFechas();

    @Query("SELECT COUNT(r) FROM RegistroAsistencia r WHERE r.fecha = :fecha AND r.condicionIngreso = 'A Tiempo'")
    long countATiempoPorFecha(@Param("fecha") LocalDate fecha);

    @Query("SELECT COUNT(r) FROM RegistroAsistencia r WHERE r.fecha = :fecha AND r.condicionIngreso = 'Tardanza'")
    long countTardanzasPorFecha(@Param("fecha") LocalDate fecha);

    @Query("SELECT COUNT(r) FROM RegistroAsistencia r WHERE r.fecha = :fecha AND r.condicionIngreso LIKE '%Omisión%'")
    long countOmisionEntradaPorFecha(@Param("fecha") LocalDate fecha);

    @Query("SELECT COUNT(r) FROM RegistroAsistencia r WHERE r.fecha = :fecha AND r.estadoCumplimiento LIKE 'Incompleto%'")
    long countIncompletosPorFecha(@Param("fecha") LocalDate fecha);
}

