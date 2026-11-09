package pe.gob.prorridre.asistencia.zkbio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO de la respuesta paginada del endpoint de marcaciones de ZKBio Time.
 *
 * <p>Estructura real de la respuesta:
 * <pre>
 * {
 *   "count":   160000,
 *   "next":    "http://192.168.1.101:8085/iclock/api/transactions/?page=2&page_size=1000&...",
 *   "previous": null,
 *   "msg":     "OK",
 *   "code":    0,
 *   "data":    [ {...}, {...}, ... ]
 * }
 * </pre>
 *
 * <p>El lector sigue el campo {@code next} hasta que sea {@code null}
 * para paginar sin límite de registros.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaginaTransacciones {

    /** Total de marcaciones que coinciden con el filtro (en todas las páginas). */
    @JsonProperty("count")
    private Integer count;

    /**
     * URL de la siguiente página, o {@code null} si ya no hay más.
     * El lector consume páginas mientras este campo no sea null.
     */
    @JsonProperty("next")
    private String next;

    /** URL de la página anterior (no se usa en el lector; solo para registro). */
    @JsonProperty("previous")
    private String previous;

    /** Mensaje de estado de ZKBio Time (normalmente "OK"). */
    @JsonProperty("msg")
    private String msg;

    /** Código de resultado de ZKBio Time (0 = éxito). */
    @JsonProperty("code")
    private Integer code;

    /** Marcaciones de la página actual. */
    @JsonProperty("data")
    private List<TransaccionDto> data;

    /* ------------------------------------------------------------------
     * Getters y Setters
     * ---------------------------------------------------------------- */

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }

    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }

    public String getPrevious() { return previous; }
    public void setPrevious(String previous) { this.previous = previous; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }

    public List<TransaccionDto> getData() { return data; }
    public void setData(List<TransaccionDto> data) { this.data = data; }

    /** Indica si hay más páginas que consumir. */
    public boolean hayMasPaginas() {
        return next != null && !next.isBlank();
    }

    @Override
    public String toString() {
        int qty = (data != null) ? data.size() : 0;
        return "PaginaTransacciones{count=" + count + ", siguiente=" + (next != null) +
               ", registrosEnPagina=" + qty + "}";
    }
}
