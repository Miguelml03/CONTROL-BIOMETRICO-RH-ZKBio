package pe.gob.prorridre.asistencia.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Marcación cruda tal como la envía ZKBio Time.
 *
 * <p>Esta tabla es de SOLO LECTURA respecto al reloj biométrico:
 * únicamente se escribe en la base de datos local; nunca se modifica ni
 * se elimina nada en ZKBio Time.
 *
 * <p>Campos excluidos intencionalmente (datos sensibles o innecesarios):
 * <ul>
 *   <li>temperatura – dato de salud; no se almacena</li>
 *   <li>is_mask – irrelevante para asistencia</li>
 *   <li>first_name, last_name – nombre y apellido informados por ZKBio Time</li>
 *   <li>department, position, emp – siempre null en los datos reales</li>
 * </ul>
 *
 * <p>La columna {@code rawJson} conserva la respuesta completa de ZKBio para
 * auditoría y para extraer campos adicionales sin nueva consulta al reloj.
 */
@Entity
@Table(name = "marcacion",
       indexes = {
           @Index(name = "idx_marcacion_punch_time", columnList = "punch_time"),
           @Index(name = "idx_marcacion_emp_code",   columnList = "emp_code"),
           @Index(name = "idx_marcacion_upload_time",columnList = "upload_time")
       })
public class Marcacion {

    /**
     * ID original de ZKBio Time – clave primaria.
     * Se usa el mismo ID para evitar duplicados de forma natural.
     */
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Código único del empleado en ZKBio Time (p. ej. "00123"). */
    @Column(name = "emp_code", nullable = false, length = 50)
    private String empCode;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /**
     * Momento en que el empleado marcó en el reloj.
     * Hora local (America/Lima) sin zona horaria, tal como la devuelve ZKBio.
     * <strong>SIEMPRE usar este campo para cálculos, nunca uploadTime.</strong>
     */
    @Column(name = "punch_time", nullable = false)
    private LocalDateTime punchTime;

    /**
     * Código de estado de la marcación (como texto): "0", "1", "4", "5".
     * El significado real está en la tabla {@link EstadoMarcacion}.
     */
    @Column(name = "punch_state", nullable = false, length = 5)
    private String punchState;

    /**
     * Tipo de verificación: 1 = Huella dactilar, 15 = Reconocimiento facial.
     * No se almacenan las plantillas biométricas (Ley 29733).
     */
    @Column(name = "verify_type")
    private Integer verifyType;

    /** Número de serie del terminal biométrico (p. ej. "COVG230760188"). */
    @Column(name = "terminal_sn", length = 50)
    private String terminalSn;

    /**
     * Momento en que el reloj subió la marcación al servidor.
     * El reloj sube por lotes y con retraso; NO usar para cálculos de puntualidad.
     */
    @Column(name = "upload_time")
    private LocalDateTime uploadTime;

    /**
     * JSON crudo completo de la marcación tal como lo devolvió ZKBio Time.
     * Permite recuperar cualquier campo adicional sin volver a consultar el reloj.
     * Columna TEXT (sin límite fijo de longitud).
     */
    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    /**
     * Momento en que se insertó este registro en la BD local.
     * Solo para trazabilidad interna; no proviene de ZKBio.
     */
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /* ------------------------------------------------------------------
     * Ciclo de vida JPA
     * ---------------------------------------------------------------- */

    /** Se rellena automáticamente al persistir por primera vez. */
    @PrePersist
    public void antesDeGuardar() {
        this.creadoEn = LocalDateTime.now();
    }

    /* ------------------------------------------------------------------
     * Getters y Setters
     * ---------------------------------------------------------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpCode() { return empCode; }
    public void setEmpCode(String empCode) { this.empCode = empCode; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDateTime getPunchTime() { return punchTime; }
    public void setPunchTime(LocalDateTime punchTime) { this.punchTime = punchTime; }

    public String getPunchState() { return punchState; }
    public void setPunchState(String punchState) { this.punchState = punchState; }

    public Integer getVerifyType() { return verifyType; }
    public void setVerifyType(Integer verifyType) { this.verifyType = verifyType; }

    public String getTerminalSn() { return terminalSn; }
    public void setTerminalSn(String terminalSn) { this.terminalSn = terminalSn; }

    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public LocalDateTime getCreadoEn() { return creadoEn; }

    @Override
    public String toString() {
        return "Marcacion{id=" + id + ", empCode='" + empCode + "'" +
               ", punchTime=" + punchTime + ", punchState='" + punchState + "'}";
    }
}
