package pe.gob.prorridre.asistencia.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.prorridre.asistencia.dto.UsuarioDto;
import pe.gob.prorridre.asistencia.security.Rol;
import pe.gob.prorridre.asistencia.security.Usuario;
import pe.gob.prorridre.asistencia.security.UsuarioRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepo, PasswordEncoder passwordEncoder) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto> listarUsuarios() {
        return usuarioRepo.findAll().stream()
                .map(this::aDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioDto obtenerPorId(Long id) {
        return usuarioRepo.findById(id)
                .map(this::aDto)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
    }

    @Transactional
    public UsuarioDto crearUsuario(UsuarioDto dto) {
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio.");
        }
        String cleanUsername = dto.getUsername().trim().toLowerCase();
        if (usuarioRepo.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException("Ya existe un usuario con el nombre: " + cleanUsername);
        }
        if (dto.getPassword() == null || dto.getPassword().trim().length() < 4) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 4 caracteres.");
        }

        Usuario u = new Usuario();
        u.setUsername(cleanUsername);
        u.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
        u.setNombreCompleto(dto.getNombreCompleto() != null ? dto.getNombreCompleto().trim() : cleanUsername);
        u.setRol(dto.getRol() != null ? dto.getRol() : Rol.ROLE_RRHH);
        u.setActivo(dto.getActivo() != null ? dto.getActivo() : true);

        Usuario guardado = usuarioRepo.save(u);
        return aDto(guardado);
    }

    @Transactional
    public UsuarioDto actualizarUsuario(Long id, UsuarioDto dto) {
        Usuario u = usuarioRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        if (dto.getNombreCompleto() != null) {
            u.setNombreCompleto(dto.getNombreCompleto().trim());
        }
        if (dto.getRol() != null) {
            u.setRol(dto.getRol());
        }
        if (dto.getActivo() != null) {
            u.setActivo(dto.getActivo());
        }
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            if (dto.getPassword().trim().length() < 4) {
                throw new IllegalArgumentException("La nueva contraseña debe tener al menos 4 caracteres.");
            }
            u.setPassword(passwordEncoder.encode(dto.getPassword().trim()));
        }

        Usuario guardado = usuarioRepo.save(u);
        return aDto(guardado);
    }

    @Transactional
    public void eliminarUsuario(Long id) {
        Usuario u = usuarioRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        // Proteger cuenta principal de administrador
        if ("doctor".equalsIgnoreCase(u.getUsername()) || "admin".equalsIgnoreCase(u.getUsername())) {
            throw new IllegalArgumentException("No se pueden eliminar las cuentas maestras de sistema (doctor / admin).");
        }

        usuarioRepo.delete(u);
    }

    private UsuarioDto aDto(Usuario u) {
        return new UsuarioDto(
                u.getId(),
                u.getUsername(),
                u.getNombreCompleto(),
                u.getRol(),
                u.getActivo()
        );
    }
}

