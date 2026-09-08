package cl.jctapia.prestamos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Metadatos que Swagger UI muestra en la cabecera de la documentacion.
 *
 * A diferencia del resto de microservicios de la biblioteca, aqui no se declara
 * un esquema de seguridad: ms-prestamos es autonomo y no valida tokens JWT, asi
 * que anunciar un candado "Authorize" que no hace nada solo confundiria a quien
 * consuma la API.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String nombreServicio;

    @Bean
    public OpenAPI prestamosOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API " + nombreServicio)
                        .version("1.0.0")
                        .description("Microservicio de gestion de prestamos de la biblioteca. "
                                + "Permite registrar el retiro de un ejemplar, consultar el historial "
                                + "de un socio y mantener los datos del prestamo. Un ejemplar no puede "
                                + "figurar en dos prestamos vigentes de forma simultanea.")
                        .contact(new Contact()
                                .name("Juan Carlos Tapia")
                                .email("tapiaonatejuancarlos@gmail.com"))
                        .license(new License()
                                .name("Uso academico - Duoc UC")));
    }
}
