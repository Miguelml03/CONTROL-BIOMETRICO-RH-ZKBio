package pe.gob.prorridre.asistencia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoConfigFinancieraRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.JustificacionAuditoriaRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.JustificarRequestDto;
import pe.gob.prorridre.asistencia.dto.LiquidacionPagoDto;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.EmpleadoConfigFinanciera;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import pe.gob.prorridre.asistencia.service.PayrollService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private RegistroAsistenciaRepository registroRepo;
    @Mock
    private EmpleadoRepository empleadoRepo;
    @Mock
    private DepartamentoRepository departamentoRepo;
    @Mock
    private EmpleadoConfigFinancieraRepository configRepo;
    @Mock
    private JustificacionAuditoriaRepository justificacionRepo;

    private PayrollService payrollService;

    @BeforeEach
    void setUp() {
        payrollService = new PayrollService(registroRepo, empleadoRepo, departamentoRepo, configRepo, justificacionRepo);
    }

    @Test
    @DisplayName("Cálculo financiero base: Sueldo S/ 3000 -> valorDia=100, valorHora=12.50, valorMinuto=0.2083")
    void testCalculoValoresDerivados() {
        EmpleadoConfigFinanciera conf = new EmpleadoConfigFinanciera("101");
        conf.setSueldoMensual(new BigDecimal("3000.00"));
        conf.setDiasBaseMes(30);
        conf.setHorasBaseDia(8);

        assertEquals(0, new BigDecimal("100.00").compareTo(conf.getValorDia().setScale(2, BigDecimal.ROUND_HALF_UP)));
        assertEquals(0, new BigDecimal("12.50").compareTo(conf.getValorHora().setScale(2, BigDecimal.ROUND_HALF_UP)));
        assertEquals(0, new BigDecimal("0.2083").compareTo(conf.getValorMinuto().setScale(4, BigDecimal.ROUND_HALF_UP)));
    }

    @Test
    @DisplayName("Liquidación con tardanza: 60 minutos de tardanza -> descuento exacto")
    void testLiquidacionConTardanzas() {
        String empId = "260";
        Empleado emp = new Empleado(empId, "LUZ GABRIELA", "HUARACHI", "PRORRIDRE", "ESTUDIOS");
        when(empleadoRepo.findById(empId)).thenReturn(Optional.of(emp));

        EmpleadoConfigFinanciera conf = new EmpleadoConfigFinanciera(empId);
        conf.setSueldoMensual(new BigDecimal("3000.00"));
        when(configRepo.findByEmpId(empId)).thenReturn(Optional.of(conf));

        LocalDate dia = LocalDate.of(2026, 3, 2); // Lunes
        RegistroAsistencia reg = new RegistroAsistencia();
        reg.setId(1L);
        reg.setEmpId(empId);
        reg.setFecha(dia);
        reg.setNumMarcaciones(4);
        reg.setHoraEntrada(LocalTime.of(9, 0, 0));
        reg.setCondicionIngreso("Tardanza");
        reg.setMinutosTardanza(60L);
        reg.setHorasEfectivasDecimal(7.0);
        reg.setBalanceJornada(-1.0);
        reg.setEstadoCumplimiento("Déficit de Jornada");

        when(registroRepo.findDistinctFechas()).thenReturn(Collections.singletonList(dia));
        when(registroRepo.findByEmpIdAndFechaBetweenOrderByFechaAsc(empId, dia, dia))
                .thenReturn(Collections.singletonList(reg));

        LiquidacionPagoDto liq = payrollService.liquidarEmpleadoIndividual(empId, dia, dia);

        assertNotNull(liq);
        assertEquals(60L, liq.getTotalMinutosTardanza());
        // 60 min * 0.208333... = S/ 12.50
        assertEquals(new BigDecimal("12.50"), liq.getDescuentoTardanzas());
        // Déficit de 1 hora * 12.50 = S/ 12.50
        assertEquals(new BigDecimal("12.50"), liq.getDescuentoDeficitHoras());
        // Descuento total = 25.00
        assertEquals(new BigDecimal("25.00"), liq.getTotalDescuentos());
        // Neto = 3000 - 25 = 2975.00
        assertEquals(new BigDecimal("2975.00"), liq.getNetoAPagar());
    }

    @Test
    @DisplayName("Acción Rápida del Doctor: Justificar tardanza médica anula el descuento")
    void testJustificacionDoctorAnulaTardanza() {
        Long regId = 50L;
        RegistroAsistencia reg = new RegistroAsistencia();
        reg.setId(regId);
        reg.setEmpId("300");
        reg.setCondicionIngreso("Tardanza");
        reg.setMinutosTardanza(45L);
        reg.setBalanceJornada(-0.75);

        when(registroRepo.findById(regId)).thenReturn(Optional.of(reg));
        when(registroRepo.save(any(RegistroAsistencia.class))).thenAnswer(i -> i.getArgument(0));

        JustificarRequestDto req = new JustificarRequestDto("JUSTIFICADO_MEDICO", "Certificado médico presentado");
        RegistroAsistencia justificado = payrollService.justificarRegistro(regId, req);

        assertEquals(0L, justificado.getMinutosTardanza());
        assertEquals(45L, justificado.getMinutosTardanzaOriginal());
        assertEquals("JUSTIFICADO_MEDICO", justificado.getEstadoJustificacion());
        assertEquals("Certificado médico presentado", justificado.getObservacionDoctor());
        verify(justificacionRepo, times(1)).save(any());
    }

    @Test
    @DisplayName("Acción Rápida del Doctor: APLICAR_DESCUENTO restablece los valores originales")
    void testAplicarDescuentoRestableceOriginal() {
        Long regId = 51L;
        RegistroAsistencia reg = new RegistroAsistencia();
        reg.setId(regId);
        reg.setEmpId("300");
        reg.setMinutosTardanza(0L);
        reg.setMinutosTardanzaOriginal(55L);
        reg.setBalanceJornada(0.0);
        reg.setBalanceJornadaOriginal(-1.2);
        reg.setEstadoJustificacion("JUSTIFICADO_MEDICO");

        when(registroRepo.findById(regId)).thenReturn(Optional.of(reg));
        when(registroRepo.save(any(RegistroAsistencia.class))).thenAnswer(i -> i.getArgument(0));

        JustificarRequestDto req = new JustificarRequestDto("APLICAR_DESCUENTO", "No sustentó justificación");
        RegistroAsistencia ratificado = payrollService.justificarRegistro(regId, req);

        assertEquals(55L, ratificado.getMinutosTardanza());
        assertEquals(-1.2, ratificado.getBalanceJornada());
        assertEquals("APLICAR_DESCUENTO", ratificado.getEstadoJustificacion());
    }
}

