package pe.gob.prorridre.asistencia.domain.repository;

import pe.gob.prorridre.asistencia.model.EmpleadoConfigFinanciera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoConfigFinancieraRepository extends JpaRepository<EmpleadoConfigFinanciera, Long> {

    Optional<EmpleadoConfigFinanciera> findByEmpId(String empId);

    List<EmpleadoConfigFinanciera> findAllByOrderByEmpIdAsc();
}

