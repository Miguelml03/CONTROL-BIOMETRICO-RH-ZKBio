package pe.gob.prorridre.asistencia.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entidad que representa a un empleado registrado en PRORRIDRE PUNO.
 * Almacena los datos maestros canónicos (ID biométrico, nombres, apellidos,
 * cargo, fecha de contratación y departamento jerárquico).
 */
@Entity
@Table(name = "empleado",
       indexes = {
           @Index(name = "idx_emp_departamento_id", columnList = "departamento_id"),
           @Index(name = "idx_emp_apellidos", columnList = "apellidos")
       })
public class Empleado {

    /** Columna A: ID/Código biométrico (ej. "1", "60", "124") */
    @Id
    @Column(name = "emp_id", nullable = false, length = 50)
    private String empId;

    /** Columna B: Nombres reales del empleado */
    @Column(name = "nombres", length = 150)
    private String nombres;

    /** Columna C: Apellidos reales del empleado */
    @Column(name = "apellidos", length = 150)
    private String apellidos;

    /** Cargo o posición institucional (ej. "ESPECIALISTA DE LIQUIDACION") */
    @Column(name = "cargo", length = 150)
    private String cargo;

    /** Fecha de ingreso / contratación */
    @Column(name = "fecha_contratacion")
    private LocalDate fechaContratacion;

    /** Empresa / Entidad (ej. "PRORRIDRE PUNO") */
    @Column(name = "empresa", length = 100)
    private String empresa = "PRORRIDRE PUNO";

    /** Relación muchos a uno con la jerarquía de Departamentos */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    /** Nombre de respaldo de departamento (por si no tiene departamento_id asignado) */
    @Column(name = "departamento_nombre", length = 150)
    private String departamentoNombre;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public Empleado() {}

    public Empleado(String empId, String nombres, String apellidos, String empresa, String departamentoNombre) {
        this.empId = empId != null ? empId.trim() : "";
        this.nombres = nombres != null ? nombres.trim() : "";
        this.apellidos = apellidos != null ? apellidos.trim() : "";
        this.empresa = (empresa != null && !empresa.trim().isEmpty()) ? empresa.trim() : "PRORRIDRE PUNO";
        this.departamentoNombre = departamentoNombre != null ? departamentoNombre.trim() : "GENERAL";
        this.activo = true;
    }

    public Empleado(String empId, String nombres, String apellidos, String cargo, LocalDate fechaContratacion, Departamento departamento) {
        this.empId = empId != null ? empId.trim() : "";
        this.nombres = nombres != null ? nombres.trim() : "";
        this.apellidos = apellidos != null ? apellidos.trim() : "";
        this.cargo = cargo != null ? cargo.trim() : "";
        this.fechaContratacion = fechaContratacion;
        this.departamento = departamento;
        if (departamento != null) {
            this.departamentoNombre = departamento.getNombre();
        }
        this.activo = true;
    }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public LocalDate getFechaContratacion() { return fechaContratacion; }
    public void setFechaContratacion(LocalDate fechaContratacion) { this.fechaContratacion = fechaContratacion; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public Departamento getDepartamento() { return departamento; }
    public void setDepartamento(Departamento departamento) {
        this.departamento = departamento;
        if (departamento != null) {
            this.departamentoNombre = departamento.getNombre();
        }
    }

    public String getDepartamentoNombre() {
        if (departamento != null && departamento.getNombre() != null) {
            return departamento.getNombre();
        }
        return departamentoNombre != null ? departamentoNombre : "SIN DEPARTAMENTO";
    }

    public void setDepartamentoNombre(String departamentoNombre) {
        this.departamentoNombre = departamentoNombre;
    }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    /**
     * Nombre completo formateado canónicamente: "APELLIDOS NOMBRES".
     */
    public String getNombreCompleto() {
        String ape = apellidos != null ? apellidos.trim() : "";
        String nom = nombres != null ? nombres.trim() : "";
        if (ape.isEmpty() && nom.isEmpty()) {
            return "EMPLEADO DESCONOCIDO - ID " + empId;
        }
        if (ape.isEmpty()) return nom;
        if (nom.isEmpty()) return ape;
        return ape + " " + nom;
    }

    /**
     * Determina si el empleado fue creado automáticamente como desconocido
     * y requiere actualización de datos por el Administrador/Doctor.
     */
    public boolean isDesconocido() {
        if (nombres == null || apellidos == null) return true;
        String full = (apellidos + " " + nombres).toUpperCase();
        return full.contains("DESCONOCIDO") || (nombres.trim().isEmpty() && apellidos.trim().isEmpty());
    }
}
