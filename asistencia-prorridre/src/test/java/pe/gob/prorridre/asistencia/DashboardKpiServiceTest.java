package pe.gob.prorridre.asistencia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.DashboardKpiDto;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import pe.gob.prorridre.asistencia.service.DashboardKpiService;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardKpiServiceTest {

    @Mock
    private RegistroAsistenciaRepository registroRepo;
    @Mock
    private EmpleadoRepository empleadoRepo;
    @Mock
    private DepartamentoRepository departamentoRepo;

    private DashboardKpiService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardKpiService(registroRepo, empleadoRepo, departamentoRepo);
    }

    @Test
    @DisplayName("Cálculo de KPIs de Puntualidad y Ausentismo Real (Días-Hombre)")
    void testCalculoKpisYAusentismo() {
        LocalDate lunes = LocalDate.of(2026, 3, 2);
        LocalDate martes = LocalDate.of(2026, 3, 3);

        when(registroRepo.findDistinctFechas()).thenReturn(Arrays.asList(martes, lunes));

        Empleado emp1 = new Empleado("101", "CARLOS", "QUISPE", "PRORRIDRE", "ESTUDIOS");
        Empleado emp2 = new Empleado("102", "MARIA", "MAMANI", "PRORRIDRE", "ADMINISTRACION");
        when(empleadoRepo.findByActivoTrueOrderByDepartamentoAscApellidosAsc())
                .thenReturn(Arrays.asList(emp1, emp2));

        // Emp1 asistió lunes y martes
        RegistroAsistencia r1 = new RegistroAsistencia();
        r1.setEmpId("101");
        r1.setDepartamento("ESTUDIOS");
        r1.setFecha(lunes);
        r1.setNumMarcaciones(4);
        r1.setCondicionIngreso("A Tiempo");
        r1.setMinutosTardanza(0L);
        r1.setHorasEfectivasDecimal(8.0);
        r1.setBalanceJornada(0.0);
        r1.setEstadoCumplimiento("Jornada Reglamentaria");

        RegistroAsistencia r2 = new RegistroAsistencia();
        r2.setEmpId("101");
        r2.setDepartamento("ESTUDIOS");
        r2.setFecha(martes);
        r2.setNumMarcaciones(4);
        r2.setCondicionIngreso("Tardanza");
        r2.setMinutosTardanza(25L);
        r2.setHorasEfectivasDecimal(7.5);
        r2.setBalanceJornada(-0.5);
        r2.setEstadoCumplimiento("Déficit de Jornada");

        // Emp2 asistió solo lunes, faltó el martes (0 marcaciones)
        RegistroAsistencia r3 = new RegistroAsistencia();
        r3.setEmpId("102");
        r3.setDepartamento("ADMINISTRACION");
        r3.setFecha(lunes);
        r3.setNumMarcaciones(4);
        r3.setCondicionIngreso("A Tiempo");
        r3.setMinutosTardanza(0L);
        r3.setHorasEfectivasDecimal(8.5);
        r3.setBalanceJornada(0.5);
        r3.setEstadoCumplimiento("Excedente de Jornada");

        when(registroRepo.findByFechaBetweenOrderByFechaDescDepartamentoAscApellidosAsc(lunes, martes))
                .thenReturn(Arrays.asList(r1, r2, r3));

        DashboardKpiDto kpis = dashboardService.calcularKpis(lunes, martes, null, null);

        assertNotNull(kpis);
        assertEquals(3, kpis.getTotalRegistrosEvaluados());
        assertEquals(2, kpis.getTotalEmpleadosActivos());
        assertEquals(2, kpis.getTotalDiasHabilesRango());

        // Ausentismo real: Emp2 no asistió el martes -> 1 día-hombre falta
        assertEquals(1, kpis.getDiasHombreFalta());
        assertEquals(1, kpis.getDetalleAusencias().size());
        assertEquals("102", kpis.getDetalleAusencias().get(0).getEmpId());

        // Puntualidad: 2 A Tiempo (66.67%), 1 Tardanza (33.33%)
        assertEquals(2, kpis.getCountATiempo());
        assertEquals(1, kpis.getCountTardanza());
        assertEquals(25L, kpis.getTotalMinutosTardanza());

        // Horas: 8.0 + 7.5 + 8.5 = 24.0
        assertEquals(24.0, kpis.getTotalHorasEfectivas());
        assertEquals(0.5, kpis.getTotalHorasExtra());
        assertEquals(0.5, kpis.getTotalHorasDeficit());

        // Ranking por departamento
        assertEquals(2, kpis.getRankingDepartamentos().size());
    }
}

