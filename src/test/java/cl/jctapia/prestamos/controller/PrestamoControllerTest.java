package cl.jctapia.prestamos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.jctapia.prestamos.dto.PrestamoRequest;
import cl.jctapia.prestamos.dto.PrestamoResponse;
import cl.jctapia.prestamos.exception.BusinessRuleException;
import cl.jctapia.prestamos.exception.EntityNotFoundException;
import cl.jctapia.prestamos.model.EstadoPrestamo;
import cl.jctapia.prestamos.service.PrestamoService;

/**
 * Pruebas del contrato HTTP de PrestamoController.
 *
 * Levanta solo la capa web (controlador + GlobalExceptionHandler + Jackson +
 * HATEOAS) y sustituye el servicio por un mock: lo que se verifica es que cada
 * endpoint traduzca bien la peticion y el resultado a codigos HTTP, cuerpo y
 * enlaces, sin depender de la base de datos.
 */
@WebMvcTest(PrestamoController.class)
@DisplayName("PrestamoController - contrato HTTP")
class PrestamoControllerTest {

    private static final String BASE = "/api/v1/prestamos";

    private static final String JSON_VALIDO = """
            {
              "codigoLibro": "BIB-1001",
              "rutUsuario": "18345678-9",
              "fechaPrestamo": "2026-09-01",
              "fechaVencimiento": "2026-09-15",
              "observacion": "Retiro en meson central"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrestamoService prestamoService;

    private PrestamoResponse respuesta(Long id, EstadoPrestamo estado) {
        PrestamoResponse respuesta = new PrestamoResponse();
        respuesta.setId(id);
        respuesta.setCodigoLibro("BIB-1001");
        respuesta.setRutUsuario("18345678-9");
        respuesta.setFechaPrestamo(LocalDate.of(2026, 9, 1));
        respuesta.setFechaVencimiento(LocalDate.of(2026, 9, 15));
        respuesta.setEstado(estado);
        return respuesta;
    }

    // ─── GET ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET / devuelve 200 con los prestamos y el enlace self de la coleccion")
    void findAll_devuelve200ConLosPrestamos() throws Exception {
        when(prestamoService.findAll()).thenReturn(List.of(respuesta(1L, EstadoPrestamo.VIGENTE)));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.prestamoResponseList[0].codigoLibro").value("BIB-1001"))
                .andExpect(jsonPath("$._embedded.prestamoResponseList[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$._links.self.href", endsWith(BASE)));
    }

    @Test
    @DisplayName("GET /{id} ofrece el enlace de devolucion solo si el prestamo esta VIGENTE")
    void findById_incluyeEnlaceDeDevolucion_cuandoElPrestamoEstaVigente() throws Exception {
        when(prestamoService.findById(1L)).thenReturn(respuesta(1L, EstadoPrestamo.VIGENTE));

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fechaPrestamo").value("2026-09-01"))
                .andExpect(jsonPath("$._links.self.href", endsWith(BASE + "/1")))
                .andExpect(jsonPath("$._links.devolucion.href", endsWith(BASE + "/1/devolucion")));
    }

    @Test
    @DisplayName("GET /{id} no ofrece el enlace de devolucion cuando el prestamo ya fue devuelto")
    void findById_omiteEnlaceDeDevolucion_cuandoElPrestamoYaFueDevuelto() throws Exception {
        when(prestamoService.findById(2L)).thenReturn(respuesta(2L, EstadoPrestamo.DEVUELTO));

        mockMvc.perform(get(BASE + "/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.devolucion").doesNotExist());
    }

    @Test
    @DisplayName("GET /{id} devuelve 404 con el sobre de error cuando el prestamo no existe")
    void findById_devuelve404_cuandoElPrestamoNoExiste() throws Exception {
        when(prestamoService.findById(99L)).thenThrow(new EntityNotFoundException("Prestamos", "ID", 99L));

        mockMvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("99")))
                .andExpect(jsonPath("$.path").value(BASE + "/99"));
    }

    @Test
    @DisplayName("GET /{id} devuelve 400 cuando el id no es numerico")
    void findById_devuelve400_cuandoElIdNoEsNumerico() throws Exception {
        mockMvc.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("'id'")))
                .andExpect(jsonPath("$.message", containsString("'abc'")));

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("GET /usuario/{rut} devuelve el historial del socio")
    void findByRutUsuario_devuelve200ConElHistorial() throws Exception {
        when(prestamoService.findByRutUsuario("18345678-9"))
                .thenReturn(List.of(respuesta(1L, EstadoPrestamo.VIGENTE), respuesta(2L, EstadoPrestamo.DEVUELTO)));

        mockMvc.perform(get(BASE + "/usuario/18345678-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.prestamoResponseList", hasSize(2)))
                .andExpect(jsonPath("$._links.self.href", endsWith(BASE + "/usuario/18345678-9")));
    }

    @Test
    @DisplayName("GET /atrasados devuelve 200 aunque no haya atrasos")
    void findAtrasados_devuelve200_cuandoNoHayAtrasos() throws Exception {
        when(prestamoService.findAtrasados()).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/atrasados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href", endsWith(BASE + "/atrasados")))
                .andExpect(jsonPath("$._embedded").doesNotExist());
    }

    // ─── POST ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST / devuelve 201 con el prestamo creado y delega en el servicio")
    void create_devuelve201_cuandoLosDatosSonValidos() throws Exception {
        when(prestamoService.create(any(PrestamoRequest.class)))
                .thenReturn(respuesta(7L, EstadoPrestamo.VIGENTE));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.estado").value("VIGENTE"));

        verify(prestamoService).create(any(PrestamoRequest.class));
    }

    @Test
    @DisplayName("POST / devuelve 400 con TODOS los campos invalidos y no llega al servicio")
    void create_devuelve400ConTodosLosErrores_cuandoLosDatosSonInvalidos() throws Exception {
        String invalido = """
                { "codigoLibro": "x", "rutUsuario": "123" }
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*]", hasItems(
                        startsWith("codigoLibro:"),
                        startsWith("rutUsuario:"),
                        startsWith("fechaPrestamo:"),
                        startsWith("fechaVencimiento:"))));

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("POST / devuelve 400 cuando la fecha no respeta el formato yyyy-MM-dd")
    void create_devuelve400_cuandoLaFechaTieneFormatoInvalido() throws Exception {
        String fechaMala = JSON_VALIDO.replace("2026-09-01", "01/09/2026");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(fechaMala))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("yyyy-MM-dd")));

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("POST / devuelve 400 cuando el cuerpo no es un JSON valido")
    void create_devuelve400_cuandoElJsonEstaMalformado() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{ no es json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("POST / devuelve 409 cuando el ejemplar ya esta prestado")
    void create_devuelve409_cuandoSeRompeUnaReglaDeNegocio() throws Exception {
        when(prestamoService.create(any(PrestamoRequest.class)))
                .thenThrow(new BusinessRuleException("El ejemplar 'BIB-1001' ya tiene un prestamo vigente"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("BIB-1001")));
    }

    // ─── PUT ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id} devuelve 200 con el prestamo actualizado")
    void update_devuelve200_cuandoElPrestamoExiste() throws Exception {
        when(prestamoService.update(any(Long.class), any(PrestamoRequest.class)))
                .thenReturn(respuesta(1L, EstadoPrestamo.VIGENTE));

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /{id} devuelve 404 cuando el prestamo no existe")
    void update_devuelve404_cuandoElPrestamoNoExiste() throws Exception {
        when(prestamoService.update(any(Long.class), any(PrestamoRequest.class)))
                .thenThrow(new EntityNotFoundException("Prestamos", "ID", 99L));

        mockMvc.perform(put(BASE + "/99").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
    }

    // ─── PATCH ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /{id}/devolucion devuelve 200 con el prestamo cerrado")
    void registrarDevolucion_devuelve200_cuandoElPrestamoEstaVigente() throws Exception {
        PrestamoResponse devuelto = respuesta(1L, EstadoPrestamo.DEVUELTO);
        devuelto.setFechaDevolucion(LocalDate.of(2026, 9, 10));
        when(prestamoService.registrarDevolucion(1L)).thenReturn(devuelto);

        mockMvc.perform(patch(BASE + "/1/devolucion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"))
                .andExpect(jsonPath("$.fechaDevolucion").value("2026-09-10"));
    }

    @Test
    @DisplayName("PATCH /{id}/devolucion devuelve 409 cuando el prestamo ya no esta vigente")
    void registrarDevolucion_devuelve409_cuandoElPrestamoNoEstaVigente() throws Exception {
        when(prestamoService.registrarDevolucion(1L))
                .thenThrow(new BusinessRuleException("El prestamo 1 no esta vigente"));

        mockMvc.perform(patch(BASE + "/1/devolucion"))
                .andExpect(status().isConflict());
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} devuelve 204 sin cuerpo")
    void deleteById_devuelve204_cuandoSeElimina() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(prestamoService).deleteById(1L);
    }

    @Test
    @DisplayName("DELETE /{id} devuelve 409 cuando el prestamo esta VIGENTE")
    void deleteById_devuelve409_cuandoElPrestamoEstaVigente() throws Exception {
        doThrow(new BusinessRuleException("El prestamo 1 esta VIGENTE y no puede eliminarse"))
                .when(prestamoService).deleteById(1L);

        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("VIGENTE")));
    }

    @Test
    @DisplayName("DELETE /{id} devuelve 404 cuando el prestamo no existe")
    void deleteById_devuelve404_cuandoElPrestamoNoExiste() throws Exception {
        doThrow(new EntityNotFoundException("Prestamos", "ID", 99L))
                .when(prestamoService).deleteById(99L);

        mockMvc.perform(delete(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    // ─── Errores no previstos ─────────────────────────────────────────────────

    @Test
    @DisplayName("Un fallo inesperado devuelve 500 sin filtrar el detalle tecnico al cliente")
    void errorInesperado_devuelve500SinFiltrarElDetalleTecnico() throws Exception {
        when(prestamoService.findAll())
                .thenThrow(new IllegalStateException("Fallo JDBC en la tabla prestamos"));

        mockMvc.perform(get(BASE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Ocurrio un error inesperado en el servidor"))
                .andExpect(content().string(not(containsString("JDBC"))));
    }
}
