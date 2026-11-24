package pe.gob.prorridre.asistencia.dto;

/**
 * DTO para la tabla de "RESUMEN INDIVIDUAL" que se presenta al pie
 * de la matriz diaria tanto en el Frontend web como en el Excel exportado.
 */
public class ResumenIndividualDto {

    private String empId;
    private String nombreCompleto;

    /** Días del periodo donde tuvo al menos 1 marcación */
    private int diasConRegistro;

    /** Cantidad de días con tardanza de ingreso */
    private int tardanzasIngresoDias;

    /** Sumatoria total de minutos de tardanza en el periodo */
    private int totalMinutosTardanza;

    // Matriz de los 4 Pilares (Total Esperados vs No Registra)
    private int totalEsperadoIngreso;
    private int noRegistraIngreso;

    private int totalEsperadoSalidaRefrigerio;
    private int noRegistraSalidaRefrigerio;

    private int totalEsperadoRetornoRefrigerio;
    private int noRegistraRetornoRefrigerio;

    private int totalEsperadoSalidaFinal;
    private int noRegistraSalidaFinal;

    /** Cantidad de días donde otrasMarcaciones no está vacío */
    private int diasConMarcacionesFueraDeRango;

    public ResumenIndividualDto() {}

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public int getDiasConRegistro() { return diasConRegistro; }
    public void setDiasConRegistro(int diasConRegistro) { this.diasConRegistro = diasConRegistro; }

    public int getTardanzasIngresoDias() { return tardanzasIngresoDias; }
    public void setTardanzasIngresoDias(int tardanzasIngresoDias) { this.tardanzasIngresoDias = tardanzasIngresoDias; }

    public int getTotalMinutosTardanza() { return totalMinutosTardanza; }
    public void setTotalMinutosTardanza(int totalMinutosTardanza) { this.totalMinutosTardanza = totalMinutosTardanza; }

    public int getTotalEsperadoIngreso() { return totalEsperadoIngreso; }
    public void setTotalEsperadoIngreso(int totalEsperadoIngreso) { this.totalEsperadoIngreso = totalEsperadoIngreso; }

    public int getNoRegistraIngreso() { return noRegistraIngreso; }
    public void setNoRegistraIngreso(int noRegistraIngreso) { this.noRegistraIngreso = noRegistraIngreso; }

    public int getTotalEsperadoSalidaRefrigerio() { return totalEsperadoSalidaRefrigerio; }
    public void setTotalEsperadoSalidaRefrigerio(int totalEsperadoSalidaRefrigerio) { this.totalEsperadoSalidaRefrigerio = totalEsperadoSalidaRefrigerio; }

    public int getNoRegistraSalidaRefrigerio() { return noRegistraSalidaRefrigerio; }
    public void setNoRegistraSalidaRefrigerio(int noRegistraSalidaRefrigerio) { this.noRegistraSalidaRefrigerio = noRegistraSalidaRefrigerio; }

    public int getTotalEsperadoRetornoRefrigerio() { return totalEsperadoRetornoRefrigerio; }
    public void setTotalEsperadoRetornoRefrigerio(int totalEsperadoRetornoRefrigerio) { this.totalEsperadoRetornoRefrigerio = totalEsperadoRetornoRefrigerio; }

    public int getNoRegistraRetornoRefrigerio() { return noRegistraRetornoRefrigerio; }
    public void setNoRegistraRetornoRefrigerio(int noRegistraRetornoRefrigerio) { this.noRegistraRetornoRefrigerio = noRegistraRetornoRefrigerio; }

    public int getTotalEsperadoSalidaFinal() { return totalEsperadoSalidaFinal; }
    public void setTotalEsperadoSalidaFinal(int totalEsperadoSalidaFinal) { this.totalEsperadoSalidaFinal = totalEsperadoSalidaFinal; }

    public int getNoRegistraSalidaFinal() { return noRegistraSalidaFinal; }
    public void setNoRegistraSalidaFinal(int noRegistraSalidaFinal) { this.noRegistraSalidaFinal = noRegistraSalidaFinal; }

    public int getDiasConMarcacionesFueraDeRango() { return diasConMarcacionesFueraDeRango; }
    public void setDiasConMarcacionesFueraDeRango(int diasConMarcacionesFueraDeRango) { this.diasConMarcacionesFueraDeRango = diasConMarcacionesFueraDeRango; }
}
