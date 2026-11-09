-- ==============================================================================
--  data.sql  –  Datos iniciales de la tabla ESTADO_MARCACION
-- ==============================================================================
--
--  Esta tabla es la "fuente de verdad" del SIGNIFICADO REAL de cada código
--  que envía ZKBio Time.  Se puede editar desde la pantalla de configuración
--  (Fase 4) sin recompilar la aplicación.
--
--  Los estados fueron verificados con 4 696 marcaciones reales (22/09–19/10/2026).
--
--  Columnas:
--    punch_state       → código numérico que envía ZKBio (como texto)
--    nombre_zkbio      → etiqueta que muestra ZKBio (a menudo engañosa)
--    significado_real  → nombre correcto para PRORRIDRE
--    hora_tipica       → referencia horaria observada en los datos reales
--    activo            → permite desactivar un estado sin borrarlo
--    orden_secuencia   → posición esperada en la jornada 0-5-4-1
-- ==============================================================================

-- Evitar duplicados en reinicios con spring.sql.init.mode=always
MERGE INTO estado_marcacion (punch_state, nombre_zkbio, significado_real, hora_tipica, activo, orden_secuencia)
KEY (punch_state)
VALUES
    ('0', 'Entrada',           'Entrada',              '~07:59', true, 1),
    ('5', 'Salida Hora Extra', 'Salida a refrigerio',  '~13:02', true, 2),
    ('4', 'Entrada Hora Extra','Regreso de refrigerio','~13:56', true, 3),
    ('1', 'Salida',            'Salida final',         '~18:25', true, 4);
