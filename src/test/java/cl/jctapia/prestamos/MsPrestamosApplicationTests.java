package cl.jctapia.prestamos;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cl.jctapia.prestamos.controller.PrestamoController;
import cl.jctapia.prestamos.repository.PrestamoRepository;

/**
 * Prueba de humo del contexto de Spring.
 *
 * Levanta la aplicacion completa con el perfil dev (H2 en memoria) para
 * detectar temprano los fallos que las pruebas unitarias con mocks no ven:
 * un bean sin declarar, una consulta derivada mal escrita en el repositorio o
 * un application.yml con una propiedad invalida.
 */
@SpringBootTest
@DisplayName("ms-prestamos - arranque del contexto")
class MsPrestamosApplicationTests {

    @Autowired
    private PrestamoController prestamoController;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Test
    @DisplayName("El contexto levanta y expone el controlador y el repositorio")
    void contextLoads() {
        assertNotNull(prestamoController, "El controlador de prestamos no se registro en el contexto");
        assertNotNull(prestamoRepository, "El repositorio de prestamos no se registro en el contexto");
    }
}
