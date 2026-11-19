package pe.gob.prorridre.asistencia;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoConfigFinancieraRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.model.EmpleadoConfigFinanciera;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;
import pe.gob.prorridre.asistencia.service.AttendanceEngineService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Punto de entrada principal del sistema de asistencia PRORRIDRE PUNO.
 *
 * <p>@EnableScheduling activa el lector programado de marcaciones
 * ({@link pe.gob.prorridre.asistencia.scheduler.LectorMarcaciones}).
 */
@SpringBootApplication
@EnableScheduling
public class AsistenciaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsistenciaApplication.class, args);
    }

    @Bean
    public ApplicationRunner inicializadorReglasToleranciaYPracticantes(
            RegistroAsistenciaRepository registroRepo,
            EmpleadoRepository empleadoRepo,
            EmpleadoConfigFinancieraRepository configRepo,
            AttendanceEngineService engineService) {
        return args -> {
            // 1. Recalcular tardanzas según la regla de tolerancia (hasta 08:10:00 no afecta; después de dicha hora se computa el exceso)
            List<RegistroAsistencia> todos = registroRepo.findAll();
            for (RegistroAsistencia r : todos) {
                if (r.getHoraEntrada() != null) {
                    engineService.reevaluarTardanzaYCumplimiento(r);
                }
            }
            if (!todos.isEmpty()) {
                registroRepo.saveAll(todos);
            }

            // 2. Establecer sueldo base S/ 0.00 para Practicantes (no reciben salario ni se les aplican descuentos salariales)
            List<Empleado> practicantes = empleadoRepo.findAll().stream()
                    .filter(e -> (e.getDepartamentoNombre() != null && e.getDepartamentoNombre().toUpperCase().contains("PRACTICANTE")) ||
                                 (e.getCargo() != null && e.getCargo().toUpperCase().contains("PRACTICANTE")))
                    .toList();

            for (Empleado p : practicantes) {
                EmpleadoConfigFinanciera conf = configRepo.findByEmpId(p.getEmpId())
                        .orElseGet(() -> new EmpleadoConfigFinanciera(p.getEmpId()));
                conf.setSueldoMensual(BigDecimal.ZERO);
                configRepo.save(conf);
            }
        };
    }
}
