package pe.gob.prorridre.asistencia.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de auditoría de justificaciones aplicadas por el Doctor
 * sobre filas específicas de RegistroAsistencia.
 */
@Entity
@Table(name = "justificacion_auditoria",
       indexes = {
           @Index(name = "idx_jaud_registro", columnList = "registro_id"),
           @Index(name = "idx_jaud_emp_id", columnList = "emp_id")
       })
public class JustificacionAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del RegistroAsistencia que fue justificado */
    @Column(name = "registro_id", nullable = false)
    private Long registroId;

    /** Código del empleado (redundante para consultas rápidas) */
    @Column(name = "emp_id", nullable = false, length = 50)
    private String empId;

    /**
     * Tipo de justificación aplicada por el Doctor:
     * JUSTIFICADO_MEDICO, COMISION_SERVICIO, PAPELETA_PERMISO,
     * REGULARIZADO_POR_DIRECTOR, APLICAR_DESCUENTO
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_justificacion", nullable = false, length = 40)
    private TipoJustificacion tipoJustificacion;

    /** Observación o nota del Doctor al registrar la justificación */
    @Column(name = "observacion", length = 500)
    private String observacion;

    /** Minutos de tardanza que se anulan/justifican (0 si no aplica) */
    @Column(name = "minutos_exonerados", nullable = false)
    private Long minutosExonerados = 0L;

    /** Horas de déficit que se anulan/justifican (0.0 si no aplica) */
    @Column(name = "horas_exoneradas", nullable = false)
    private Double horasExoneradas = 0.0;

    /** Indica si esta justificación está vigente (false = anulada) */
    @Column(name = "vigente", nullable = false)
    private Boolean vigente = true;

    /** Quién aplicó la justificación */
    @Column(name = "aplicado_por", nullable = false, length = 100)
    private String aplicadoPor = "DOCTOR";

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    public void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    public JustificacionAuditoria() {}

    // ── Enum ────────────────────────────────────────────────────────────────

    public enum TipoJustificacion {
        JUSTIFICADO_MEDICO,
        COMISION_SERVICIO,
        PAPELETA_PERMISO,
        REGULARIZADO_POR_DIRECTOR,
        APLICAR_DESCUENTO
    }

    // ── Getters y Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRegistroId() { return registroId; }
    public void setRegistroId(Long registroId) { this.registroId = registroId; }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public TipoJustificacion getTipoJustificacion() { return tipoJustificacion; }
    public void setTipoJustificacion(TipoJustificacion tipoJustificacion) { this.tipoJustificacion = tipoJustificacion; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public Long getMinutosExonerados() { return minutosExonerados; }
    public void setMinutosExonerados(Long minutosExonerados) { this.minutosExonerados = minutosExonerados; }

    public Double getHorasExoneradas() { return horasExoneradas; }
    public void setHorasExoneradas(Double horasExoneradas) { this.horasExoneradas = horasExoneradas; }

    public Boolean getVigente() { return vigente; }
    public void setVigente(Boolean vigente) { this.vigente = vigente; }

    public String getAplicadoPor() { return aplicadoPor; }
    public void setAplicadoPor(String aplicadoPor) { this.aplicadoPor = aplicadoPor; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}

