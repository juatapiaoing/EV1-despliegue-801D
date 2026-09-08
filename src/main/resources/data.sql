-- ============================================================
-- Datos de prueba del perfil dev (H2 en memoria)
-- ============================================================
-- Se cargan en cada arranque local para poder probar la API en
-- Swagger sin registrar prestamos a mano. El perfil prod tiene
-- spring.sql.init.mode=never, de modo que este archivo jamas se
-- ejecuta contra la base de datos de la instancia EC2.
-- ============================================================

INSERT INTO prestamos (codigo_libro, rut_usuario, fecha_prestamo, fecha_vencimiento, fecha_devolucion, estado, observacion) VALUES
  ('BIB-1001', '18345678-9', '2026-08-10', '2026-08-24', '2026-08-22', 'DEVUELTO',  'Devuelto en buen estado'),
  ('BIB-1002', '18345678-9', '2026-09-01', '2026-09-15', NULL,         'VIGENTE',   NULL),
  ('BIB-2050', '20111222-3', '2026-08-18', '2026-09-01', NULL,         'VIGENTE',   'Socio solicito renovacion'),
  ('BIB-3007', '15987654-K', '2026-07-05', '2026-07-19', '2026-07-30', 'DEVUELTO',  'Devuelto con atraso de 11 dias'),
  ('BIB-4120', '20111222-3', '2026-09-03', '2026-09-17', NULL,         'CANCELADO', 'Error de registro en meson');
