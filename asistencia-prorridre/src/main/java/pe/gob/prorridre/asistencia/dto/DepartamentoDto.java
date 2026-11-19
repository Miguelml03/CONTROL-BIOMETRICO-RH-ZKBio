package pe.gob.prorridre.asistencia.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO para transferir información jerárquica de Departamentos y Metas.
 */
public class DepartamentoDto {

    private Long id;
    private String codigo;
    private String nombre;
    private Long parentId;
    private String parentNombre;
    private String rutaJerarquica;
    private Integer nivel;
    private Long totalEmpleados;
    private List<DepartamentoDto> hijos = new ArrayList<>();

    public DepartamentoDto() {}

    public DepartamentoDto(Long id, String codigo, String nombre, Long parentId, String parentNombre, String rutaJerarquica, Integer nivel) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
        this.parentId = parentId;
        this.parentNombre = parentNombre;
        this.rutaJerarquica = rutaJerarquica;
        this.nivel = nivel;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getParentNombre() { return parentNombre; }
    public void setParentNombre(String parentNombre) { this.parentNombre = parentNombre; }

    public String getRutaJerarquica() { return rutaJerarquica; }
    public void setRutaJerarquica(String rutaJerarquica) { this.rutaJerarquica = rutaJerarquica; }

    public Integer getNivel() { return nivel; }
    public void setNivel(Integer nivel) { this.nivel = nivel; }

    public Long getTotalEmpleados() { return totalEmpleados; }
    public void setTotalEmpleados(Long totalEmpleados) { this.totalEmpleados = totalEmpleados; }

    public List<DepartamentoDto> getHijos() { return hijos; }
    public void setHijos(List<DepartamentoDto> hijos) { this.hijos = hijos; }
}

