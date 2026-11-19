package pe.gob.prorridre.asistencia.security;

/**
 * Roles de seguridad en el Sistema de Asistencia PRORRIDRE:
 * - ROLE_ADMIN: Acceso total (Doctor / Director). Lectura, escritura, edición, borrado, justificaciones y cambios de sueldo.
 * - ROLE_RRHH: Solo lectura (Recursos Humanos). Dashboard, consultas, búsqueda y exportación Excel/PDF.
 */
public enum Rol {
    ROLE_ADMIN,
    ROLE_RRHH
}

