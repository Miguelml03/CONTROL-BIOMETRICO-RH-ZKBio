package pe.gob.prorridre.asistencia.zkbio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de una marcación individual tal como la devuelve ZKBio Time.
 *
 * <p>Campos reales verificados con datos del reloj COVG230760188:
 * <ul>
 *   <li>{@code id} – identificador único en ZKBio Time (clave primaria local)</li>
 *   <li>{@code emp_code} – código del empleado</li>
 *   <li>{@code first_name}, {@code last_name} – nombre y apellido, si están disponibles</li>
 *   <li>{@code punch_time} – "YYYY-MM-DD HH:mm:ss", hora local, SIN zona horaria</li>
 *   <li>{@code punch_state} – "0","1","4","5" como texto</li>
 *   <li>{@code punch_state_display} – etiqueta ZKBio (puede ser engañosa)</li>
 *   <li>{@code verify_type} – 1 = Huella, 15 = Rostro</li>
 *   <li>{@code terminal_sn} – serie del reloj</li>
 *   <li>{@code upload_time} – cuándo subió el reloj; NO usar para cálculos</li>
 * </ul>
 *
 * <p>Campos deliberadamente NO guardados:
 * {@code temperature}, {@code is_mask} (irrelevantes),
 * {@code emp}, {@code department}, {@code position} (siempre null en los datos reales).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} absorbe campos
 * que ZKBio pueda añadir en futuras versiones sin romper el parseo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransaccionDto {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("emp_code")
    private String empCode;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("punch_time")
    private String punchTime;          // formato: "2025-07-19 12:10:05"

    @JsonProperty("punch_state")
    private String punchState;         // "0", "1", "4" o "5" como texto

    @JsonProperty("punch_state_display")
    private String punchStateDisplay;  // etiqueta ZKBio (puede ser engañosa)

    @JsonProperty("verify_type")
    private Integer verifyType;        // 1=Huella, 15=Rostro

    @JsonProperty("verify_type_display")
    private String verifyTypeDisplay;

    @JsonProperty("terminal_sn")
    private String terminalSn;

    @JsonProperty("upload_time")
    private String uploadTime;         // formato: "2025-07-19 12:15:00"

    @JsonProperty("work_code")
    private String workCode;

    @JsonProperty("gps_location")
    private String gpsLocation;

    @JsonProperty("area_alias")
    private String areaAlias;

    @JsonProperty("terminal_alias")
    private String terminalAlias;

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

    public String getPunchTime() { return punchTime; }
    public void setPunchTime(String punchTime) { this.punchTime = punchTime; }

    public String getPunchState() { return punchState; }
    public void setPunchState(String punchState) { this.punchState = punchState; }

    public String getPunchStateDisplay() { return punchStateDisplay; }
    public void setPunchStateDisplay(String punchStateDisplay) { this.punchStateDisplay = punchStateDisplay; }

    public Integer getVerifyType() { return verifyType; }
    public void setVerifyType(Integer verifyType) { this.verifyType = verifyType; }

    public String getVerifyTypeDisplay() { return verifyTypeDisplay; }
    public void setVerifyTypeDisplay(String verifyTypeDisplay) { this.verifyTypeDisplay = verifyTypeDisplay; }

    public String getTerminalSn() { return terminalSn; }
    public void setTerminalSn(String terminalSn) { this.terminalSn = terminalSn; }

    public String getUploadTime() { return uploadTime; }
    public void setUploadTime(String uploadTime) { this.uploadTime = uploadTime; }

    public String getWorkCode() { return workCode; }
    public void setWorkCode(String workCode) { this.workCode = workCode; }

    public String getGpsLocation() { return gpsLocation; }
    public void setGpsLocation(String gpsLocation) { this.gpsLocation = gpsLocation; }

    public String getAreaAlias() { return areaAlias; }
    public void setAreaAlias(String areaAlias) { this.areaAlias = areaAlias; }

    public String getTerminalAlias() { return terminalAlias; }
    public void setTerminalAlias(String terminalAlias) { this.terminalAlias = terminalAlias; }

    @Override
    public String toString() {
        return "TransaccionDto{id=" + id + ", empCode='" + empCode + "'" +
               ", punchTime='" + punchTime + "', punchState='" + punchState + "'}";
    }
}
