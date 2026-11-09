package pe.gob.prorridre.asistencia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Prueba de carga del contexto de Spring Boot.
 *
 * <p>Usa propiedades de prueba para sustituir las variables de entorno
 * ZK_USER y ZK_PASS sin necesitar el servidor ZKBio Time real.
 * En modo test, el lector programado no arranca (el servidor biométrico
 * no está disponible en el entorno de pruebas).
 */
@SpringBootTest
@TestPropertySource(properties = {
    // Credenciales ficticias para que AppConfig no falle en el test
    "zkbio.usuario=test_user",
    "zkbio.clave=test_pass",
    // Usar H2 en memoria para los tests (no contaminar el archivo de datos)
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    // No arrancar el lector automático en tests
    "app.lector.periodo-ms=99999999"
})
class AsistenciaApplicationTests {

    @Test
    void contextoCargaCorrectamente() {
        // Si el contexto de Spring arranca sin errores, el test pasa.
        // Verifica que todas las configuraciones y dependencias estén en orden.
    }
}
