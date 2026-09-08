-- ============================================================
-- ms-prestamos - Inicialización de la base de datos (MySQL 8.x)
-- ============================================================
-- Se ejecuta UNA sola vez sobre la instancia de MySQL del entorno
-- de producción, antes del primer arranque del microservicio.
--
-- No crea las tablas: de eso se encarga Hibernate al arrancar con
-- el perfil prod (ddl-auto = update). Este script solo prepara la
-- base y el usuario de aplicación con los permisos mínimos.
--
-- Uso:
--   mysql -u root -p < db/init-prestamos.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS prestamos
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Usuario de aplicación
-- ------------------------------------------------------------
-- El microservicio NO se conecta como root. Un usuario dedicado y
-- acotado a una sola base limita el daño si las credenciales se
-- filtran: no puede leer ni alterar el resto de esquemas del motor.
--
-- IMPORTANTE: reemplazar la contraseña antes de ejecutar el script
-- y usar exactamente ese valor en la variable de entorno
-- DB_PASSWORD de la unidad de systemd. Nunca versionar la real.
-- ------------------------------------------------------------

CREATE USER IF NOT EXISTS 'prestamos_app'@'localhost'
    IDENTIFIED BY 'CAMBIAR_ESTA_PASSWORD';

-- Se otorgan solo los permisos que el servicio necesita:
--   - DML  (SELECT/INSERT/UPDATE/DELETE) para operar sobre los datos.
--   - DDL  (CREATE/ALTER/INDEX/REFERENCES) porque ddl-auto=update
--          necesita poder crear y ajustar la tabla al desplegar.
-- Se omiten DROP y GRANT OPTION a propósito.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON prestamos.*
    TO 'prestamos_app'@'localhost';

FLUSH PRIVILEGES;

-- ------------------------------------------------------------
-- Verificación
-- ------------------------------------------------------------
SHOW DATABASES LIKE 'prestamos';
SELECT user, host FROM mysql.user WHERE user = 'prestamos_app';
