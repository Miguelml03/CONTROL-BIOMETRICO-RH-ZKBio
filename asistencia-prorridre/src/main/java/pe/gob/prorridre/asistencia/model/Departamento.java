package pe.gob.prorridre.asistencia.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entidad que representa la estructura jerárquica de Departamentos, Direcciones,
 * Oficinas y Metas presupuestales de PRORRIDRE PUNO (sincronizada desde ZKBio Time).
 *
 * Soporta árbol recursivo: Ej. "DIRECCION EJECUTIVA" -> "UEI PRORRIDRE" -> "UEI META 002".
 */
@Entity
@Table(name = "departamento",
       indexes = {
           @Index(name = "idx_dept_codigo", columnList = "codigo", unique = true),
           @Index(name = "idx_dept_nombre", columnList = "nombre")
       })
public class Departamento {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", length = 50, unique = true)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Relación recursiva con el departamento padre */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private Departamento parent;

    /** Subdepartamentos o metas hijas */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Departamento> hijos = new ArrayList<>();

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public Departamento() {}

    public Departamento(Long id, String codigo, String nombre, Departamento parent) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre != null ? nombre.trim() : "";
        this.parent = parent;
        this.activo = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre != null ? nombre.trim() : ""; }

    public Departamento getParent() { return parent; }
    public void setParent(Departamento parent) { this.parent = parent; }

    public List<Departamento> getHijos() { return hijos; }
    public void setHijos(List<Departamento> hijos) { this.hijos = hijos; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    /**
     * Construye la ruta jerárquica completa.
     * Ej: "DIRECCION EJECUTIVA / UEI PRORRIDRE / UEI META 002"
     */
    public String getRutaJerarquica() {
        if (parent == null) {
            return nombre;
        }
        return parent.getRutaJerarquica() + " / " + nombre;
    }

    /**
     * Nivel de profundidad en el árbol (0 = raíz).
     */
    public int getNivel() {
        if (parent == null) return 0;
        return parent.getNivel() + 1;
    }

    /**
     * Recolecta recursivamente todos los departamentos descendientes (incluyéndose a sí mismo).
     */
    public List<Departamento> obtenerTodosLosDescendientes() {
        List<Departamento> lista = new ArrayList<>();
        recolectarDescendientes(this, lista);
        return lista;
    }

    private void recolectarDescendientes(Departamento actual, List<Departamento> acumulador) {
        if (actual == null) return;
        acumulador.add(actual);
        if (actual.getHijos() != null) {
            for (Departamento hijo : actual.getHijos()) {
                recolectarDescendientes(hijo, acumulador);
            }
        }
    }

    /**
     * Obtiene el conjunto de todos los nombres (case-insensitive) de este nodo y sus descendientes.
     */
    public Set<String> obtenerNombresDescendientes() {
        Set<String> nombres = new HashSet<>();
        for (Departamento d : obtenerTodosLosDescendientes()) {
            if (d.getNombre() != null && !d.getNombre().trim().isEmpty()) {
                nombres.add(d.getNombre().trim().toUpperCase());
            }
        }
        return nombres;
    }
}

