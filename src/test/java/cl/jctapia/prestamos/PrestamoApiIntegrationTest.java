package cl.jctapia.prestamos;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Recorridos completos de la API contra la aplicacion real (controlador,
 * servicio, repositorio y H2), sin mocks.
 *
 * Cubren los casos criticos del negocio de punta a punta: el ciclo de vida de
 * un prestamo, la regla de "un ejemplar, un prestamo vigente" y la deteccion
 * de atrasos. Cada prueba usa su propio codigo de ejemplar para no depender
 * del orden de ejecucion ni de los datos de data.sql.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("API de prestamos - recorridos de integracion")
class PrestamoApiIntegrationTest {

    private static final String BASE = "/api/v1/prestamos";

    @Autowired
    private MockMvc mockMvc;

    /** Registra un prestamo y devuelve el id que le asigno la base de datos. */
    private long registrar(String codigoLibro, LocalDate fechaPrestamo, LocalDate fechaVencimiento) throws Exception {
        String cuerpo = crearJson(codigoLibro, fechaPrestamo, fechaVencimiento);

        String respuesta = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private String crearJson(String codigoLibro, LocalDate fechaPrestamo, LocalDate fechaVencimiento) {
        return String.format("""
                {
                  "codigoLibro": "%s",
                  "rutUsuario": "18345678-9",
                  "fechaPrestamo": "%s",
                  "fechaVencimiento": "%s"
                }
                """, codigoLibro, fechaPrestamo, fechaVencimiento);
    }

    @Test
    @DisplayName("Un prestamo recorre su ciclo completo: registrar, consultar, devolver y eliminar")
    void cicloDeVida_registrarDevolverYEliminar() throws Exception {
        LocalDate hoy = LocalDate.now();
        long id = registrar("INT-0001", hoy, hoy.plusDays(14));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.fechaDevolucion").doesNotExist())
                .andExpect(jsonPath("$._links.devolucion.href").exists());

        mockMvc.perform(patch(BASE + "/" + id + "/devolucion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"))
                .andExpect(jsonPath("$.fechaDevolucion").value(hoy.toString()));

        // Una devolucion no se puede repetir.
        mockMvc.perform(patch(BASE + "/" + id + "/devolucion"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un mismo ejemplar no puede tener dos prestamos vigentes")
    void noSePuedePrestarDosVecesElMismoEjemplar() throws Exception {
        LocalDate hoy = LocalDate.now();
        registrar("INT-0002", hoy, hoy.plusDays(14));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(crearJson("INT-0002", hoy, hoy.plusDays(7))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("INT-0002")));
    }

    @Test
    @DisplayName("Un ejemplar se puede volver a prestar una vez devuelto")
    void seAdmiteUnNuevoPrestamo_cuandoElEjemploYaFueDevuelto() throws Exception {
        LocalDate hoy = LocalDate.now();
        long id = registrar("INT-0003", hoy, hoy.plusDays(14));

        mockMvc.perform(patch(BASE + "/" + id + "/devolucion")).andExpect(status().isOk());

        registrar("INT-0003", hoy, hoy.plusDays(14));
    }

    @Test
    @DisplayName("Un prestamo VIGENTE no se puede eliminar y sigue consultable")
    void noSePuedeEliminarUnPrestamoVigente() throws Exception {
        LocalDate hoy = LocalDate.now();
        long id = registrar("INT-0004", hoy, hoy.plusDays(14));

        mockMvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("VIGENTE")));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un prestamo vencido aparece en atrasados y deja de aparecer al devolverse")
    void atrasados_incluyeLosVencidos_yExcluyeLosDevueltos() throws Exception {
        LocalDate hoy = LocalDate.now();
        long id = registrar("INT-0005", hoy.minusDays(30), hoy.minusDays(10));

        mockMvc.perform(get(BASE + "/atrasados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.prestamoResponseList[*].codigoLibro", hasItem("INT-0005")));

        mockMvc.perform(patch(BASE + "/" + id + "/devolucion")).andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/atrasados"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("INT-0005"))));
    }

    @Test
    @DisplayName("Un PUT no puede cerrar un prestamo: el estado y la devolucion no se aceptan en el JSON")
    void put_ignoraElEstadoEnviadoPorElCliente() throws Exception {
        LocalDate hoy = LocalDate.now();
        long id = registrar("INT-0006", hoy, hoy.plusDays(14));

        String conEstado = """
                {
                  "codigoLibro": "INT-0006",
                  "rutUsuario": "18345678-9",
                  "fechaPrestamo": "%s",
                  "fechaVencimiento": "%s",
                  "estado": "DEVUELTO",
                  "fechaDevolucion": "%s"
                }
                """.formatted(hoy, hoy.plusDays(21), hoy);

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(conEstado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.fechaDevolucion").doesNotExist())
                .andExpect(jsonPath("$.fechaVencimiento").value(hoy.plusDays(21).toString()));
    }

    @Test
    @DisplayName("El healthcheck de Actuator responde UP, igual que el que usa el contenedor")
    void actuatorHealth_respondeUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("La documentacion OpenAPI se genera y describe el servicio")
    void openApi_describeElServicio() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API ms-prestamos"))
                .andExpect(jsonPath("$.paths['/api/v1/prestamos']").exists());
    }
}
