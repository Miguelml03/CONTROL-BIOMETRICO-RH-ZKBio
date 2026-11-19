package pe.gob.prorridre.asistencia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pe.gob.prorridre.asistencia.dto.AuthResponseDto;
import pe.gob.prorridre.asistencia.dto.LoginRequestDto;
import pe.gob.prorridre.asistencia.security.JwtUtil;
import pe.gob.prorridre.asistencia.security.Usuario;
import pe.gob.prorridre.asistencia.security.UsuarioRepository;

import java.util.Optional;

/**
 * Controlador REST para autenticación de usuarios y emisión de Tokens JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UsuarioRepository usuarioRepo,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * POST /api/v1/auth/login
     * Autentica credenciales y genera el token de sesión JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty() ||
            req.getPassword() == null || req.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(AuthResponseDto.error("Debe ingresar usuario y contraseña."));
        }

        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsernameIgnoreCase(req.getUsername().trim());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponseDto.error("Usuario o contraseña incorrectos."));
        }

        Usuario usuario = usuarioOpt.get();
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponseDto.error("La cuenta de usuario está desactivada."));
        }

        if (!passwordEncoder.matches(req.getPassword().trim(), usuario.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponseDto.error("Usuario o contraseña incorrectos."));
        }

        String token = jwtUtil.generateToken(usuario);

        return ResponseEntity.ok(new AuthResponseDto(
                true,
                "Inicio de sesión exitoso.",
                token,
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol().name()
        ));
    }

    /**
     * GET /api/v1/auth/me
     * Devuelve los datos del usuario autenticado en la sesión actual.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponseDto.error("No autenticado."));
        }

        String username = auth.getName();
        return usuarioRepo.findByUsernameIgnoreCase(username)
                .map(u -> ResponseEntity.ok(new AuthResponseDto(
                        true,
                        "Sesión activa",
                        null,
                        u.getUsername(),
                        u.getNombreCompleto(),
                        u.getRol().name()
                )))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponseDto.error("Usuario no encontrado.")));
    }
}

