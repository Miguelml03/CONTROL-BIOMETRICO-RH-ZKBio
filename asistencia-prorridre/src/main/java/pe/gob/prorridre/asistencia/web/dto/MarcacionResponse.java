package pe.gob.prorridre.asistencia.web.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO de respuesta de la API REST interna para una marcación.
 *
 * <p>Incluye el {@code significadoReal} del estado obtenido de la tabla
 * {@code estado_marcacion}, no el nombre ZKBio.
 * La interfaz web muestra este campo directamente.
 */
public class MarcacionResponse {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private Long id;
    private String empCode;
    private String firstName;
    private String lastName;

    /** Fecha/hora de la marcación formateada para mostrar en pantalla. */
    private String punchTime;

    /** Código de estado: "0", "1", "4" o "5". */
    private String punchState;

    /**
     * Significado real del estado en PRORRIDRE PUNO.
     * Ejemplo: "Entrada", "Salida a refrigerio", etc.
     */
    private String significadoReal;

    /**
     * Etiqueta CSS para colorear el estado en la interfaz:
     * "entrada", "salida-refrigerio", "regreso-refrigerio", "salida-final", "desconocido".
     */
    private String etiquetaCss;

    /**
     * Descripción del tipo de verificación biométrica.
     * "Huella dactilar", "Reconocimiento facial" o "Desconocido (N)".
     * NUNCA se transmite la plantilla biométrica.
     */
    private String tipoVerificacion;

    private String terminalSn;

    /* ------------------------------------------------------------------
     * Constructor
     * ---------------------------------------------------------------- */

    public MarcacionResponse() {}

    /**
     * Construye el DTO a partir de los datos de la entidad y la configuración de estado.
     *
     * @param id              ID de ZKBio Time
     * @param empCode         código del empleado
     * @param firstName       nombre informado por ZKBio Time
     * @param lastName        apellido informado por ZKBio Time
     * @param punchTimeLocal  punch_time como LocalDateTime
     * @param punchState      código de estado
     * @param significadoReal nombre real del estado (de la tabla de configuración)
     * @param verifyType      código numérico del tipo de verificación
     * @param terminalSn      número de serie del terminal
     */
    public MarcacionResponse(Long id, String empCode, String firstName, String lastName,
                             LocalDateTime punchTimeLocal,
                             String punchState, String significadoReal,
                             Integer verifyType, String terminalSn) {
        this.id             = id;
        this.empCode        = empCode;
        this.firstName      = firstName;
        this.lastName       = lastName;
        this.punchTime      = punchTimeLocal != null ? punchTimeLocal.format(FMT) : "–";
        this.punchState     = punchState;
        this.significadoReal = significadoReal != null ? significadoReal : "Desconocido (" + punchState + ")";
        this.terminalSn     = terminalSn;

        // Etiqueta CSS según estado
        this.etiquetaCss = switch (punchState != null ? punchState : "") {
            case "0" -> "entrada";
            case "5" -> "salida-refrigerio";
            case "4" -> "regreso-refrigerio";
            case "1" -> "salida-final";
            default  -> "desconocido";
        };

        // Descripción del tipo de verificación
        this.tipoVerificacion = switch (verifyType != null ? verifyType : -1) {
            case  1 -> "Huella";
            case 15 -> "Rostro";
            default -> (verifyType != null ? "Tipo " + verifyType : "–");
        };
    }

    /* ------------------------------------------------------------------
     * Getters (necesarios para la serialización JSON)
     * ---------------------------------------------------------------- */

    public Long getId() { return id; }
    public String getEmpCode() { return empCode; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPunchTime() { return punchTime; }
    public String getPunchState() { return punchState; }
    public String getSignificadoReal() { return significadoReal; }
    public String getEtiquetaCss() { return etiquetaCss; }
    public String getTipoVerificacion() { return tipoVerificacion; }
    public String getTerminalSn() { return terminalSn; }
}
