package cl.jctapia.prestamos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.jctapia.prestamos.dto.PrestamoRequest;
import cl.jctapia.prestamos.dto.PrestamoResponse;
import cl.jctapia.prestamos.exception.BusinessRuleException;
import cl.jctapia.prestamos.exception.EntityNotFoundException;
import cl.jctapia.prestamos.mapper.PrestamoMapper;
import cl.jctapia.prestamos.model.EstadoPrestamo;
import cl.jctapia.prestamos.model.Prestamo;
import cl.jctapia.prestamos.repository.PrestamoRepository;

/**
 * Pruebas unitarias de las reglas de negocio de PrestamoService.
 *
 * Se sustituyen el repositorio y el mapper por mocks: lo que se quiere
 * verificar es la DECISION del servicio (aceptar, rechazar, con que mensaje),
 * no que Hibernate sepa escribir en una tabla. Eso permite ademas que el
 * pipeline de CI ejecute estos tests sin ninguna base de datos levantada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrestamoService - reglas de negocio")
class PrestamoServiceTest {

    @Mock
    private PrestamoRepository prestamoRepository;

    @Mock
    private PrestamoMapper prestamoMapper;

    @InjectMocks
    private PrestamoService prestamoService;

    private PrestamoRequest request;
    private Prestamo prestamo;

    /** Datos base validos. Cada prueba altera solo el campo que quiere probar. */
    @BeforeEach
    void setUp() {
        request = new PrestamoRequest();
        request.setCodigoLibro("BIB-1001");
        request.setRutUsuario("18345678-9");
        request.setFechaPrestamo(LocalDate.of(2026, 9, 1));
        request.setFechaVencimiento(LocalDate.of(2026, 9, 15));
        request.setObservacion("Retiro en meson central");

        prestamo = Prestamo.builder()
                .id(1L)
                .codigoLibro("BIB-1001")
                .rutUsuario("18345678-9")
                .fechaPrestamo(LocalDate.of(2026, 9, 1))
                .fechaVencimiento(LocalDate.of(2026, 9, 15))
                .estado(EstadoPrestamo.VIGENTE)
                .build();
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create deja el prestamo en estado VIGENTE cuando el ejemplar esta disponible")
    void create_asignaEstadoVigente_cuandoElEjemplarEstaDisponible() {
        when(prestamoRepository.existsByCodigoLibroAndEstado("BIB-1001", EstadoPrestamo.VIGENTE))
                .thenReturn(false);
        when(prestamoMapper.toEntity(request)).thenReturn(prestamo);
        when(prestamoRepository.save(any(Prestamo.class))).thenReturn(prestamo);
        when(prestamoMapper.toResponse(prestamo)).thenReturn(new PrestamoResponse());

        PrestamoResponse resultado = prestamoService.create(request);

        assertNotNull(resultado);

        // El estado no viene del request: lo impone el servicio. Se captura la
        // entidad que llego al repositorio para comprobarlo.
        ArgumentCaptor<Prestamo> capturado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepository).save(capturado.capture());
        assertEquals(EstadoPrestamo.VIGENTE, capturado.getValue().getEstado());
    }

    @Test
    @DisplayName("create rechaza el prestamo si el ejemplar ya esta en manos de otro socio")
    void create_lanzaBusinessRuleException_cuandoElEjemplarYaEstaPrestado() {
        when(prestamoRepository.existsByCodigoLibroAndEstado("BIB-1001", EstadoPrestamo.VIGENTE))
                .thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> prestamoService.create(request));

        assertTrue(ex.getMessage().contains("BIB-1001"));

        // Nada debe persistirse cuando la regla falla.
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    @DisplayName("create rechaza un prestamo que vence el mismo dia en que se entrega")
    void create_lanzaBusinessRuleException_cuandoElVencimientoNoEsPosteriorAlPrestamo() {
        request.setFechaVencimiento(request.getFechaPrestamo());

        assertThrows(BusinessRuleException.class, () -> prestamoService.create(request));

        // La coherencia de fechas se valida antes de tocar la base de datos.
        verify(prestamoRepository, never()).existsByCodigoLibroAndEstado(anyString(), any());
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update no revisa disponibilidad si el ejemplar del prestamo no cambia")
    void update_omiteValidacionDeDisponibilidad_cuandoElEjemplarEsElMismo() {
        when(prestamoRepository.findById(1L)).thenReturn(Optional.of(prestamo));
        when(prestamoRepository.save(prestamo)).thenReturn(prestamo);
        when(prestamoMapper.toResponse(prestamo)).thenReturn(new PrestamoResponse());

        prestamoService.update(1L, request);

        // Comprobar disponibilidad aqui daria un falso conflicto: el unico
        // prestamo vigente de ese ejemplar es el que se esta editando.
        verify(prestamoRepository, never()).existsByCodigoLibroAndEstadoAndIdNot(anyString(), any(), any());
        verify(prestamoMapper, times(1)).updateEntity(request, prestamo);
    }

    @Test
    @DisplayName("update rechaza el cambio a un ejemplar que ya esta prestado")
    void update_lanzaBusinessRuleException_cuandoElNuevoEjemplarNoEstaDisponible() {
        request.setCodigoLibro("BIB-9999");

        when(prestamoRepository.findById(1L)).thenReturn(Optional.of(prestamo));
        when(prestamoRepository.existsByCodigoLibroAndEstadoAndIdNot("BIB-9999", EstadoPrestamo.VIGENTE, 1L))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> prestamoService.update(1L, request));

        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    // ─── consultas ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById lanza 404 cuando el prestamo no existe")
    void findById_lanzaEntityNotFoundException_cuandoElPrestamoNoExiste() {
        when(prestamoRepository.findById(99L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> prestamoService.findById(99L));

        assertTrue(ex.getMessage().contains("99"));
    }

    @Test
    @DisplayName("findByRutUsuario devuelve lista vacia y no error cuando el socio no tiene historial")
    void findByRutUsuario_devuelveListaVacia_cuandoElSocioNoTienePrestamos() {
        when(prestamoRepository.findByRutUsuarioOrderByFechaPrestamoDesc("11111111-1"))
                .thenReturn(List.of());
        when(prestamoMapper.toResponseList(List.of())).thenReturn(List.of());

        List<PrestamoResponse> resultado = prestamoService.findByRutUsuario("11111111-1");

        assertTrue(resultado.isEmpty());
    }

    // ─── findAtrasados ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAtrasados consulta solo los VIGENTE vencidos antes de hoy")
    void findAtrasados_consultaVigentesVencidosAntesDeHoy() {
        Prestamo atrasado = Prestamo.builder()
                .id(7L)
                .codigoLibro("BIB-2050")
                .rutUsuario("20111222-3")
                .fechaPrestamo(LocalDate.now().minusDays(30))
                .fechaVencimiento(LocalDate.now().minusDays(16))
                .estado(EstadoPrestamo.VIGENTE)
                .build();

        when(prestamoRepository.findByEstadoAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(
                EstadoPrestamo.VIGENTE, LocalDate.now()))
                .thenReturn(List.of(atrasado));
        when(prestamoMapper.toResponseList(List.of(atrasado))).thenReturn(List.of(new PrestamoResponse()));

        List<PrestamoResponse> resultado = prestamoService.findAtrasados();

        assertEquals(1, resultado.size());

        // La regla "vigente y vencido antes de hoy" vive en el servicio: se
        // verifica que llegue exactamente ese criterio al repositorio.
        verify(prestamoRepository).findByEstadoAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(
                EstadoPrestamo.VIGENTE, LocalDate.now());
    }

    @Test
    @DisplayName("findAtrasados devuelve lista vacia cuando nadie esta atrasado")
    void findAtrasados_devuelveListaVacia_cuandoNoHayAtrasos() {
        when(prestamoRepository.findByEstadoAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(
                any(), any())).thenReturn(List.of());
        when(prestamoMapper.toResponseList(List.of())).thenReturn(List.of());

        assertTrue(prestamoService.findAtrasados().isEmpty());
    }

    // ─── registrarDevolucion ──────────────────────────────────────────────────

    @Test
    @DisplayName("registrarDevolucion cierra un prestamo VIGENTE con la fecha de hoy")
    void registrarDevolucion_marcaDevueltoYFechaActual_cuandoElPrestamoEstaVigente() {
        when(prestamoRepository.findById(1L)).thenReturn(Optional.of(prestamo));
        when(prestamoRepository.save(prestamo)).thenReturn(prestamo);
        when(prestamoMapper.toResponse(prestamo)).thenReturn(new PrestamoResponse());

        prestamoService.registrarDevolucion(1L);

        ArgumentCaptor<Prestamo> capturado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepository).save(capturado.capture());
        assertEquals(EstadoPrestamo.DEVUELTO, capturado.getValue().getEstado());
        assertEquals(LocalDate.now(), capturado.getValue().getFechaDevolucion());
    }

    @Test
    @DisplayName("registrarDevolucion rechaza un prestamo que ya fue devuelto")
    void registrarDevolucion_lanzaBusinessRuleException_cuandoElPrestamoNoEstaVigente() {
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        when(prestamoRepository.findById(1L)).thenReturn(Optional.of(prestamo));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> prestamoService.registrarDevolucion(1L));

        assertTrue(ex.getMessage().contains("DEVUELTO"));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    @DisplayName("registrarDevolucion lanza 404 cuando el prestamo no existe")
    void registrarDevolucion_lanzaEntityNotFoundException_cuandoElPrestamoNoExiste() {
        when(prestamoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> prestamoService.registrarDevolucion(99L));
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById elimina un prestamo que ya no esta vigente")
    void deleteById_eliminaElPrestamo_cuandoYaNoEstaVigente() {
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        when(prestamoRepository.findById(1L)).thenReturn(Optional.of(prestamo));

        prestamoService.deleteById(1L);

        verify(prestamoRepository, times(1)).delete(prestamo);
    }

    @Test
    @DisplayName("deleteById rechaza eliminar un prestamo VIGENTE")
    void deleteById_lanzaBusinessRuleException_cuandoElPrestamoEstaVigente() {
        when(prestamoRepository.findById(1L)).thenReturn(Optional.of(prestamo));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> prestamoService.deleteById(1L));

        assertTrue(ex.getMessage().contains("VIGENTE"));

        // El registro debe seguir intacto: el ejemplar aun no ha vuelto.
        verify(prestamoRepository, never()).delete(any(Prestamo.class));
    }
}
