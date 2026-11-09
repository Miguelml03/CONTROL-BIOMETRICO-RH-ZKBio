package pe.gob.prorridre.asistencia.domain.entity;

import jakarta.persistence.*;

/**
 * Tabla de configuración de estados de marcación.
 *
 * <p>Mapea los códigos numéricos que envía ZKBio Time ("0","1","4","5")
 * con el SIGNIFICADO REAL en PRORRIDRE PUNO, verificado con datos históricos.
 *
 * <p>Esta tabla es editable desde la pantalla de configuración (Fase 4);
 * no hay ningún estado "fijo en código".  Los datos iniciales se cargan
 * en {@code src/main/resources/data.sql}.
 *
 * <p>Secuencia normal de una jornada completa: 0 → 5 → 4 → 1
 * (se cumple en el 58 % de los días según los datos reales).
 */
@Entity
@Table(name = "estado_marcacion")
public class EstadoMarcacion {

    /**
     * Código de punch_state que envía ZKBio Time (almacenado como texto
     * para no perder eventuales valores con cero inicial o no numéricos).
     */
    @Id
    @Column(name = "punch_state", nullable = false, length = 5)
    private String punchState;

    /**
     * Etiqueta que muestra ZKBio Time en su interfaz (puede ser engañosa;
     * se guarda solo como referencia).
     * Ejemplo: "Salida Hora Extra" en realidad significa "Salida a refrigerio".
     */
    @Column(name = "nombre_zkbio", nullable = false, length = 100)
    private String nombreZkbio;

    /**
     * Nombre correcto del estado en el contexto de PRORRIDRE PUNO.
     * Este es el texto que muestra la interfaz web del sistema.
     */
    @Column(name = "significado_real", nullable = false, length = 100)
    private String significadoReal;

    /**
     * Hora típica observada en los datos históricos.
     * Solo informativa; no se usa en cálculos.
     */
    @Column(name = "hora_tipica", length = 20)
    private String horaTipica;

    /**
     * Indica si este estado está activo.
     * Permite desactivar un estado sin borrarlo
     * (útil si ZKBio añade un código nuevo no conocido aún).
     */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /**
     * Posición esperada del estado dentro de la secuencia diaria.
     * 1=Entrada, 2=Salida a refrigerio, 3=Regreso de refrigerio, 4=Salida final.
     * Se usa en el motor de reglas (Fase 2) para detectar marcaciones fuera de orden.
     */
    @Column(name = "orden_secuencia")
    private Integer ordenSecuencia;

    /* ------------------------------------------------------------------
     * Getters y Setters
     * ---------------------------------------------------------------- */

    public String getPunchState() { return punchState; }
    public void setPunchState(String punchState) { this.punchState = punchState; }

    public String getNombreZkbio() { return nombreZkbio; }
    public void setNombreZkbio(String nombreZkbio) { this.nombreZkbio = nombreZkbio; }

    public String getSignificadoReal() { return significadoReal; }
    public void setSignificadoReal(String significadoReal) { this.significadoReal = significadoReal; }

    public String getHoraTipica() { return horaTipica; }
    public void setHoraTipica(String horaTipica) { this.horaTipica = horaTipica; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Integer getOrdenSecuencia() { return ordenSecuencia; }
    public void setOrdenSecuencia(Integer ordenSecuencia) { this.ordenSecuencia = ordenSecuencia; }

    @Override
    public String toString() {
        return "EstadoMarcacion{punchState='" + punchState + "'" +
               ", significadoReal='" + significadoReal + "'" +
               ", activo=" + activo + "}";
    }
}
