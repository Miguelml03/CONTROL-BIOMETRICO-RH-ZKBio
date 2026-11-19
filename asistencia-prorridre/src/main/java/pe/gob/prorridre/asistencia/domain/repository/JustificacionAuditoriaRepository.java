package pe.gob.prorridre.asistencia.domain.repository;

import pe.gob.prorridre.asistencia.model.JustificacionAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JustificacionAuditoriaRepository extends JpaRepository<JustificacionAuditoria, Long> {

    /** Todas las justificaciones vigentes para un registro de asistencia */
    List<JustificacionAuditoria> findByRegistroIdAndVigenteTrue(Long registroId);

    /** Historial completo de justificaciones de un empleado */
    List<JustificacionAuditoria> findByEmpIdOrderByCreadoEnDesc(String empId);

    /** Sumatoria de minutos exonerados vigentes para un registro */
    default long sumMinutosExonerados(Long registroId) {
        return findByRegistroIdAndVigenteTrue(registroId)
                .stream()
                .mapToLong(j -> j.getMinutosExonerados() != null ? j.getMinutosExonerados() : 0L)
                .sum();
    }

    /** Sumatoria de horas exoneradas vigentes para un registro */
    default double sumHorasExoneradas(Long registroId) {
        return findByRegistroIdAndVigenteTrue(registroId)
                .stream()
                .mapToDouble(j -> j.getHorasExoneradas() != null ? j.getHorasExoneradas() : 0.0)
                .sum();
    }
}

