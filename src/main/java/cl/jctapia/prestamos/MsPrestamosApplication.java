package cl.jctapia.prestamos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de prestamos.
 *
 * Es un servicio autonomo: no se registra en Eureka, no depende de un API
 * Gateway y no expone seguridad JWT. Se levanta con un unico .jar ejecutable,
 * lo que permite desplegarlo como una unidad independiente en la instancia EC2.
 */
@SpringBootApplication
public class MsPrestamosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsPrestamosApplication.class, args);
    }
}
