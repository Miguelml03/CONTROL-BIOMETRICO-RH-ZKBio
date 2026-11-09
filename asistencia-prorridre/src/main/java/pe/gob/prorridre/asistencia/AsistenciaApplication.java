package pe.gob.prorridre.asistencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
