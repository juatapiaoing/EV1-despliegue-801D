package cl.jctapia.prestamos.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import cl.jctapia.prestamos.dto.PrestamoRequest;
import cl.jctapia.prestamos.dto.PrestamoResponse;
import cl.jctapia.prestamos.model.EstadoPrestamo;
import cl.jctapia.prestamos.model.Prestamo;

/**
 * Pruebas del mapper que MapStruct genera.
 *
 * Lo importante aqui no es que copie campos, sino que NO copie los que el
 * servicio controla: si alguien quita un @Mapping(ignore = true), un cliente
 * podria cerrar un prestamo o cambiar su id desde el JSON de un PUT.
 */
@DisplayName("PrestamoMapper - campos protegidos")
class PrestamoMapperTest {

    private final PrestamoMapper mapper = Mappers.getMapper(PrestamoMapper.class);

    private PrestamoRequest request() {
        PrestamoRequest request = new PrestamoRequest();
        request.setCodigoLibro("BIB-7777");
        request.setRutUsuario("20111222-3");
        request.setFechaPrestamo(LocalDate.of(2026, 10, 1));
        request.setFechaVencimiento(LocalDate.of(2026, 10, 15));
        request.setObservacion("Renovacion solicitada");
        return request;
    }

    @Test
    @DisplayName("toEntity copia los datos del request y deja el id y la devolucion sin asignar")
    void toEntity_copiaLosDatos_yNoAsignaIdNiDevolucion() {
        Prestamo entidad = mapper.toEntity(request());

        assertEquals("BIB-7777", entidad.getCodigoLibro());
        assertEquals("20111222-3", entidad.getRutUsuario());
        assertEquals(LocalDate.of(2026, 10, 1), entidad.getFechaPrestamo());
        assertEquals(LocalDate.of(2026, 10, 15), entidad.getFechaVencimiento());
        assertEquals("Renovacion solicitada", entidad.getObservacion());
        assertNull(entidad.getId());
        assertNull(entidad.getFechaDevolucion());
    }

    @Test
    @DisplayName("updateEntity actualiza los datos pero respeta id, estado y fecha de devolucion")
    void updateEntity_noAlteraIdEstadoNiFechaDeDevolucion() {
        Prestamo existente = Prestamo.builder()
                .id(5L)
                .codigoLibro("BIB-0001")
                .rutUsuario("18345678-9")
                .fechaPrestamo(LocalDate.of(2026, 8, 1))
                .fechaVencimiento(LocalDate.of(2026, 8, 15))
                .fechaDevolucion(LocalDate.of(2026, 8, 12))
                .estado(EstadoPrestamo.DEVUELTO)
                .build();

        mapper.updateEntity(request(), existente);

        assertEquals("BIB-7777", existente.getCodigoLibro());
        assertEquals("20111222-3", existente.getRutUsuario());
        assertEquals(LocalDate.of(2026, 10, 15), existente.getFechaVencimiento());

        assertEquals(5L, existente.getId());
        assertEquals(EstadoPrestamo.DEVUELTO, existente.getEstado());
        assertEquals(LocalDate.of(2026, 8, 12), existente.getFechaDevolucion());
    }

    @Test
    @DisplayName("toResponse copia todos los campos de la entidad, incluido el estado")
    void toResponse_copiaTodosLosCampos() {
        Prestamo entidad = Prestamo.builder()
                .id(3L)
                .codigoLibro("BIB-2050")
                .rutUsuario("20111222-3")
                .fechaPrestamo(LocalDate.of(2026, 8, 18))
                .fechaVencimiento(LocalDate.of(2026, 9, 1))
                .fechaDevolucion(LocalDate.of(2026, 8, 30))
                .estado(EstadoPrestamo.DEVUELTO)
                .observacion("Sin novedades")
                .build();

        PrestamoResponse respuesta = mapper.toResponse(entidad);

        assertEquals(3L, respuesta.getId());
        assertEquals("BIB-2050", respuesta.getCodigoLibro());
        assertEquals(LocalDate.of(2026, 8, 30), respuesta.getFechaDevolucion());
        assertEquals(EstadoPrestamo.DEVUELTO, respuesta.getEstado());
        assertEquals("Sin novedades", respuesta.getObservacion());
    }

    @Test
    @DisplayName("toResponseList conserva el orden y tolera una lista vacia")
    void toResponseList_conservaElOrden() {
        Prestamo primero = Prestamo.builder().id(1L).codigoLibro("BIB-0001").build();
        Prestamo segundo = Prestamo.builder().id(2L).codigoLibro("BIB-0002").build();

        List<PrestamoResponse> respuestas = mapper.toResponseList(List.of(primero, segundo));

        assertEquals(List.of(1L, 2L), respuestas.stream().map(PrestamoResponse::getId).toList());
        assertTrue(mapper.toResponseList(List.of()).isEmpty());
    }
}
