package pe.gob.prorridre.asistencia.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Configuración financiera por empleado que el Doctor puede editar.
 * Almacena el sueldo mensual y los valores derivados para calcular descuentos.
 */
@Entity
@Table(name = "empleado_config_financiera",
       indexes = { @Index(name = "idx_ecf_emp_id", columnList = "emp_id", unique = true) })
public class EmpleadoConfigFinanciera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código/ID del empleado en ZKBio Time (FK lógico a empleado.emp_id) */
    @Column(name = "emp_id", nullable = false, length = 50, unique = true)
    private String empId;

    /**
     * Sueldo mensual en Soles. Valor por defecto: S/ 3,000.00.
     * El Doctor puede ajustarlo por trabajador o por cargo.
     */
    @Column(name = "sueldo_mensual", nullable = false, precision = 10, scale = 2)
    private BigDecimal sueldoMensual = new BigDecimal("3000.00");

    /** Base de días al mes para el cálculo de descuentos (default 30). */
    @Column(name = "dias_base_mes", nullable = false)
    private Integer diasBaseMes = 30;

    /** Horas de jornada base por día (default 8). */
    @Column(name = "horas_base_dia", nullable = false)
    private Integer horasBaseDia = 8;

    /**
     * Monto fijo de multa por cada omisión de marcado no justificada.
     * El Doctor puede configurarlo. Si es 0, se convierte a medio día de descuento.
     */
    @Column(name = "multa_omision", nullable = false, precision = 8, scale = 2)
    private BigDecimal multaOmision = BigDecimal.ZERO;

    /**
     * Si es true, las horas excedente (+) compensan déficit (-).
     * Si es false, las horas excedente se pagan como bonificación separada.
     */
    @Column(name = "excedente_compensa_deficit", nullable = false)
    private Boolean excedenteCompensaDeficit = true;

    /** Cargo o puesto del empleado (referencial, no normalizado). */
    @Column(name = "cargo", length = 150)
    private String cargo;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor = "SISTEMA";

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    public EmpleadoConfigFinanciera() {}

    public EmpleadoConfigFinanciera(String empId) {
        this.empId = empId;
    }

    // ── Valores derivados (calculados en tiempo real, no persistidos) ──────────

    /** valorDia = sueldoMensual / diasBaseMes */
    @Transient
    public BigDecimal getValorDia() {
        if (sueldoMensual == null || diasBaseMes == null || diasBaseMes == 0) return BigDecimal.ZERO;
        return sueldoMensual.divide(new BigDecimal(diasBaseMes), 6, RoundingMode.HALF_UP);
    }

    /** valorHora = valorDia / horasBaseDia */
    @Transient
    public BigDecimal getValorHora() {
        if (horasBaseDia == null || horasBaseDia == 0) return BigDecimal.ZERO;
        return getValorDia().divide(new BigDecimal(horasBaseDia), 6, RoundingMode.HALF_UP);
    }

    /** valorMinuto = valorHora / 60 */
    @Transient
    public BigDecimal getValorMinuto() {
        return getValorHora().divide(new BigDecimal("60"), 6, RoundingMode.HALF_UP);
    }

    // ── Getters y Setters ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public BigDecimal getSueldoMensual() { return sueldoMensual; }
    public void setSueldoMensual(BigDecimal sueldoMensual) { this.sueldoMensual = sueldoMensual; }

    public Integer getDiasBaseMes() { return diasBaseMes; }
    public void setDiasBaseMes(Integer diasBaseMes) { this.diasBaseMes = diasBaseMes; }

    public Integer getHorasBaseDia() { return horasBaseDia; }
    public void setHorasBaseDia(Integer horasBaseDia) { this.horasBaseDia = horasBaseDia; }

    public BigDecimal getMultaOmision() { return multaOmision; }
    public void setMultaOmision(BigDecimal multaOmision) { this.multaOmision = multaOmision; }

    public Boolean getExcedenteCompensaDeficit() { return excedenteCompensaDeficit; }
    public void setExcedenteCompensaDeficit(Boolean excedenteCompensaDeficit) { this.excedenteCompensaDeficit = excedenteCompensaDeficit; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
}

