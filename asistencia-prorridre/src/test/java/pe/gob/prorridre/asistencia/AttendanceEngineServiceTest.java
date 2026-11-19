package pe.gob.prorridre.asistencia;

import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import pe.gob.prorridre.asistencia.service.AttendanceEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias del Motor Matemático de Asistencia (AttendanceEngineService)")
class AttendanceEngineServiceTest {

    private AttendanceEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new AttendanceEngineService();
    }

    @Test
    @DisplayName("Regla 1: Filtro Antirrebote (Debounce < 5 min descarta duplicados)")
    void testFiltroAntirrebote() {
        // 07:50:30 y 07:54:07 (< 5 min de diferencia -> 07:54:07 debe descartarse)
        // 13:05:00 y 13:07:00 (< 5 min -> 13:07:00 debe descartarse)
        String horas = "07:50:30, 07:54:07, 13:05:00, 13:07:00, 14:02:10, 18:25:00";
        List<LocalTime> filtradas = engine.aplicarFiltroAntirrebote(engine.parsearHoras(horas));

        assertEquals(4, filtradas.size(), "Deben quedar exactamente 4 marcaciones válidas");
        assertEquals(LocalTime.of(7, 50, 30), filtradas.get(0));
        assertEquals(LocalTime.of(13, 5, 0), filtradas.get(1));
        assertEquals(LocalTime.of(14, 2, 10), filtradas.get(2));
        assertEquals(LocalTime.of(18, 25, 0), filtradas.get(3));
    }

    @Test
    @DisplayName("Regla 1: Protección contra falsas tardanzas vespertinas (marcación única > 12:00)")
    void testMarcacionUnicaVespertina() {
        // Un trabajador marca solo a las 18:28:10 (salida)
        RegistroAsistencia reg = engine.calcularRegistro(
                "260", "LUZ GABRIELA", "HUARACHI", "PRORRIDRE", "ESTUDIOS",
                LocalDate.of(2026, 10, 31), "18:28:10"
        );

        assertNull(reg.getHoraEntrada(), "Hora de entrada debe ser null");
        assertEquals(LocalTime.of(18, 28, 10), reg.getHoraSalida(), "La única marcación debe asignarse a horaSalida");
        assertEquals(0L, reg.getMinutosTardanza(), "Minutos de tardanza deben ser 0");
        assertEquals("Sin Marcación (Omisión Entrada)", reg.getCondicionIngreso());
        assertEquals("Incompleto (Falta Salida/Entrada)", reg.getEstadoCumplimiento());
        assertNull(reg.getBalanceJornada(), "Balance de jornada debe ser null (evitando #¡VALOR!)");
    }

    @Test
    @DisplayName("Regla 3: Tolerancia Retroactiva de Ingreso (08:00 vs 08:10)")
    void testToleranciaRetroactiva() {
        LocalDate hoy = LocalDate.of(2026, 10, 31);

        // Caso A: Ingreso a tiempo (08:05:00)
        RegistroAsistencia regA = engine.calcularRegistro("1", "A", "A", "PRORRIDRE", "RRHH", hoy, "08:05:00, 18:05:00");
        assertEquals("A Tiempo", regA.getCondicionIngreso());
        assertEquals(0L, regA.getMinutosTardanza());

        // Caso B: Ingreso en el límite exacto de tolerancia (08:10:00)
        RegistroAsistencia regB = engine.calcularRegistro("2", "B", "B", "PRORRIDRE", "RRHH", hoy, "08:10:00, 18:10:00");
        assertEquals("A Tiempo", regB.getCondicionIngreso());
        assertEquals(0L, regB.getMinutosTardanza());

        // Caso C: Ingreso después de la tolerancia (08:12:00) -> Se calcula contra las 08:00:00 = 12 minutos
        RegistroAsistencia regC = engine.calcularRegistro("3", "C", "C", "PRORRIDRE", "RRHH", hoy, "08:12:00, 18:12:00");
        assertEquals("Tardanza", regC.getCondicionIngreso());
        assertEquals(12L, regC.getMinutosTardanza(), "La tardanza debe ser de 12 minutos calculados contra las 08:00");
    }

    @Test
    @DisplayName("Regla 2: Relleno de Almuerzo Reglamentario (Falta timbrado y salida >= 15:30)")
    void testAlmuerzoReglamentario() {
        LocalDate hoy = LocalDate.of(2026, 10, 31);

        // Entrada 08:00:00, Salida 18:00:00 (10 horas brutas)
        // No marcó almuerzo -> se descuenta 1 hora reglamentaria -> 9.0 horas efectivas
        RegistroAsistencia reg = engine.calcularRegistro("4", "D", "D", "PRORRIDRE", "ADMIN", hoy, "08:00:00, 18:00:00");

        assertTrue(reg.getAlmuerzoImputado(), "Debe marcarse almuerzo imputado");
        assertEquals(LocalTime.of(13, 0, 0), reg.getInicioAlmuerzo());
        assertEquals(LocalTime.of(14, 0, 0), reg.getFinAlmuerzo());
        assertEquals("09:00:00", reg.getJornadaEfectiva());
        assertEquals(9.0, reg.getHorasEfectivasDecimal());
        assertEquals(1.0, reg.getBalanceJornada());
        assertEquals("Excedente de Jornada", reg.getEstadoCumplimiento());
    }

    @Test
    @DisplayName("Regla 2: Retiro de media jornada (< 14:00) sin descuento de almuerzo")
    void testMediaJornadaSinDescuentoAlmuerzo() {
        LocalDate hoy = LocalDate.of(2026, 10, 31);

        // Entrada 08:00:00, Salida 13:00:00 (5 horas brutas)
        // Se retiró antes de las 14:00 -> Descuento almuerzo = 0
        RegistroAsistencia reg = engine.calcularRegistro("5", "E", "E", "PRORRIDRE", "ESTUDIOS", hoy, "08:00:00, 13:00:00");

        assertFalse(reg.getAlmuerzoImputado());
        assertNull(reg.getInicioAlmuerzo());
        assertEquals("05:00:00", reg.getJornadaEfectiva());
        assertEquals(5.0, reg.getHorasEfectivasDecimal());
        assertEquals(-3.0, reg.getBalanceJornada());
        assertEquals("Déficit de Jornada", reg.getEstadoCumplimiento());
    }

    @Test
    @DisplayName("Regla 3: Jornada Reglamentaria en rango de tolerancia (+/- 0.08 horas)")
    void testJornadaReglamentaria() {
        LocalDate hoy = LocalDate.of(2026, 10, 31);

        // Entrada 08:00:00, Almuerzo 13:00-14:00 (1h), Salida 17:02:00 -> 8.03 horas (+0.03 balance)
        RegistroAsistencia reg = engine.calcularRegistro("6", "F", "F", "PRORRIDRE", "ESTUDIOS", hoy,
                "08:00:00, 13:00:00, 14:00:00, 17:02:00");

        assertEquals(8.03, reg.getHorasEfectivasDecimal());
        assertEquals(0.03, reg.getBalanceJornada());
        assertEquals("Jornada Reglamentaria", reg.getEstadoCumplimiento(),
                "Un balance de +0.03 está dentro de [-0.08, +0.08] y debe ser Jornada Reglamentaria");
    }
}

