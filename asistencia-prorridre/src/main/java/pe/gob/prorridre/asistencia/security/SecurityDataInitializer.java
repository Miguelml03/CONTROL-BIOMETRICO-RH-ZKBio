package pe.gob.prorridre.asistencia.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Inicializador de usuarios y roles del sistema PRORRIDRE.
 * Garantiza que siempre existan las cuentas iniciales para el Doctor y Recursos Humanos.
 */
@Component
public class SecurityDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityDataInitializer.class);

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;

    public SecurityDataInitializer(UsuarioRepository usuarioRepo, PasswordEncoder passwordEncoder) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 1. Usuario Administrador General / Doctor (ROLE_ADMIN)
        if (!usuarioRepo.existsByUsernameIgnoreCase("admin")) {
            Usuario admin = new Usuario("admin", passwordEncoder.encode("prorridre2026"), "Administrador del Sistema", Rol.ROLE_ADMIN);
            usuarioRepo.save(admin);
            log.info("[Seguridad] Usuario 'admin' (ROLE_ADMIN) creado exitosamente.");
        }

        // 2. Usuario Doctor / Director Ejecutivo (ROLE_ADMIN)
        if (!usuarioRepo.existsByUsernameIgnoreCase("doctor")) {
            Usuario doctor = new Usuario("doctor", passwordEncoder.encode("doctor2026"), "Director Ejecutivo PRORRIDRE", Rol.ROLE_ADMIN);
            usuarioRepo.save(doctor);
            log.info("[Seguridad] Usuario 'doctor' (ROLE_ADMIN) creado exitosamente.");
        }

        // 3. Usuario Recursos Humanos (ROLE_RRHH - Solo Lectura)
        if (!usuarioRepo.existsByUsernameIgnoreCase("rrhh")) {
            Usuario rrhh = new Usuario("rrhh", passwordEncoder.encode("rrhh2026"), "Especialista RRHH PRORRIDRE", Rol.ROLE_RRHH);
            usuarioRepo.save(rrhh);
            log.info("[Seguridad] Usuario 'rrhh' (ROLE_RRHH) creado exitosamente.");
        }
    }
}

