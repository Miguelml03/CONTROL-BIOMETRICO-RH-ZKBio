package pe.gob.prorridre.asistencia.domain.repository;

import pe.gob.prorridre.asistencia.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, String> {

    @Query("SELECT e FROM Empleado e LEFT JOIN FETCH e.departamento WHERE e.activo = true ORDER BY COALESCE(e.departamento.nombre, e.departamentoNombre) ASC, e.apellidos ASC")
    List<Empleado> findByActivoTrueOrderByDepartamentoAscApellidosAsc();

    @Query("SELECT e FROM Empleado e LEFT JOIN FETCH e.departamento WHERE e.activo = true AND (LOWER(e.departamento.nombre) = LOWER(:departamento) OR LOWER(e.departamentoNombre) = LOWER(:departamento)) ORDER BY e.apellidos ASC")
    List<Empleado> findByActivoTrueAndDepartamentoIgnoreCaseOrderByApellidosAsc(@Param("departamento") String departamento);

    @Query("SELECT e FROM Empleado e LEFT JOIN FETCH e.departamento WHERE e.activo = true AND e.departamento.id = :departamentoId ORDER BY e.apellidos ASC")
    List<Empleado> findByActivoTrueAndDepartamento_Id(@Param("departamentoId") Long departamentoId);

    @Query("SELECT e FROM Empleado e LEFT JOIN FETCH e.departamento WHERE e.activo = true AND UPPER(COALESCE(e.departamento.nombre, e.departamentoNombre)) IN :departamentos ORDER BY e.apellidos ASC")
    List<Empleado> findByActivoTrueAndDepartamentoIn(@Param("departamentos") Collection<String> departamentos);

    @Query("SELECT DISTINCT COALESCE(e.departamento.nombre, e.departamentoNombre) FROM Empleado e WHERE (e.departamento IS NOT NULL OR (e.departamentoNombre IS NOT NULL AND TRIM(e.departamentoNombre) != '')) ORDER BY 1")
    List<String> findDistinctDepartamentos();

    @Query("SELECT e FROM Empleado e WHERE UPPER(e.nombres) LIKE '%DESCONOCIDO%' OR UPPER(e.apellidos) LIKE '%DESCONOCIDO%' OR e.nombres IS NULL OR TRIM(e.nombres) = ''")
    List<Empleado> findEmpleadosDesconocidos();

    long countByActivoTrue();
}
