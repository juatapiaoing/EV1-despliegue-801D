package cl.jctapia.prestamos.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cl.jctapia.prestamos.model.EstadoPrestamo;
import cl.jctapia.prestamos.model.Prestamo;

/**
 * Pruebas de las consultas derivadas de PrestamoRepository contra H2.
 *
 * Los tests con mocks no pueden detectar una consulta mal escrita ni un
 * ordenamiento al reves: aqui se ejecuta SQL real. Se desactiva la carga de
 * data.sql para que cada prueba controle exactamente los registros que ve; la
 * transaccion de @DataJpaTest se revierte al terminar cada metodo.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@DisplayName("PrestamoRepository - consultas derivadas")
class PrestamoRepositoryTest {

    private static final String RUT = "18345678-9";

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Prestamo guardar(String codigoLibro, String rut, LocalDate fechaPrestamo,
                             LocalDate fechaVencimiento, EstadoPrestamo estado) {
        return prestamoRepository.saveAndFlush(Prestamo.builder()
                .codigoLibro(codigoLibro)
                .rutUsuario(rut)
                .fechaPrestamo(fechaPrestamo)
                .fechaVencimiento(fechaVencimiento)
                .estado(estado)
                .build());
    }

    // ─── Historial por usuario ────────────────────────────────────────────────

    @Test
    @DisplayName("El historial devuelve solo los prestamos del socio, del mas reciente al mas antiguo")
    void findByRutUsuario_ordenaPorFechaDescendente_yExcluyeAOtrosSocios() {
        guardar("BIB-0001", RUT, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 24), EstadoPrestamo.DEVUELTO);
        guardar("BIB-0002", RUT, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 24), EstadoPrestamo.VIGENTE);
        guardar("BIB-0003", RUT, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 24), EstadoPrestamo.DEVUELTO);
        guardar("BIB-0004", "20111222-3", LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 24), EstadoPrestamo.VIGENTE);

        List<Prestamo> historial = prestamoRepository.findByRutUsuarioOrderByFechaPrestamoDesc(RUT);

        assertEquals(List.of("BIB-0002", "BIB-0003", "BIB-0001"),
                historial.stream().map(Prestamo::getCodigoLibro).toList());
    }

    @Test
    @DisplayName("El historial de un socio sin prestamos es una lista vacia")
    void findByRutUsuario_devuelveListaVacia_cuandoElSocioNoTienePrestamos() {
        assertTrue(prestamoRepository.findByRutUsuarioOrderByFechaPrestamoDesc("11111111-1").isEmpty());
    }

    // ─── Atrasados ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Los atrasados son solo los VIGENTE vencidos antes de hoy, del mas atrasado al mas reciente")
    void findByEstadoAndFechaVencimientoBefore_devuelveSoloLosVigentesVencidos() {
        LocalDate hoy = LocalDate.now();

        guardar("BIB-0010", RUT, hoy.minusDays(30), hoy.minusDays(1), EstadoPrestamo.VIGENTE);
        guardar("BIB-0011", RUT, hoy.minusDays(40), hoy.minusDays(10), EstadoPrestamo.VIGENTE);
        // Vence hoy: todavia no esta atrasado.
        guardar("BIB-0012", RUT, hoy.minusDays(14), hoy, EstadoPrestamo.VIGENTE);
        // Vencido pero ya cerrado o anulado: no hay ejemplar que reclamar.
        guardar("BIB-0013", RUT, hoy.minusDays(40), hoy.minusDays(20), EstadoPrestamo.DEVUELTO);
        guardar("BIB-0014", RUT, hoy.minusDays(40), hoy.minusDays(20), EstadoPrestamo.CANCELADO);

        List<Prestamo> atrasados = prestamoRepository
                .findByEstadoAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(EstadoPrestamo.VIGENTE, hoy);

        assertEquals(List.of("BIB-0011", "BIB-0010"),
                atrasados.stream().map(Prestamo::getCodigoLibro).toList());
    }

    // ─── Disponibilidad del ejemplar ──────────────────────────────────────────

    @Test
    @DisplayName("existsByCodigoLibroAndEstado distingue un ejemplar prestado de uno ya devuelto")
    void existsByCodigoLibroAndEstado_soloCuentaElEstadoConsultado() {
        guardar("BIB-0020", RUT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), EstadoPrestamo.VIGENTE);
        guardar("BIB-0021", RUT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), EstadoPrestamo.DEVUELTO);

        assertTrue(prestamoRepository.existsByCodigoLibroAndEstado("BIB-0020", EstadoPrestamo.VIGENTE));
        assertFalse(prestamoRepository.existsByCodigoLibroAndEstado("BIB-0021", EstadoPrestamo.VIGENTE));
        assertFalse(prestamoRepository.existsByCodigoLibroAndEstado("BIB-0099", EstadoPrestamo.VIGENTE));
    }

    @Test
    @DisplayName("existsByCodigoLibroAndEstadoAndIdNot no cuenta al propio prestamo que se edita")
    void existsByCodigoLibroAndEstadoAndIdNot_ignoraElRegistroExcluido() {
        Prestamo propio = guardar("BIB-0030", RUT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), EstadoPrestamo.VIGENTE);

        // El unico prestamo vigente del ejemplar es el que se esta editando.
        assertFalse(prestamoRepository.existsByCodigoLibroAndEstadoAndIdNot(
                "BIB-0030", EstadoPrestamo.VIGENTE, propio.getId()));

        // Un segundo registro de otro socio si es un conflicto real.
        assertTrue(prestamoRepository.existsByCodigoLibroAndEstadoAndIdNot(
                "BIB-0030", EstadoPrestamo.VIGENTE, propio.getId() + 1000));
    }

    // ─── Mapeo de la entidad ──────────────────────────────────────────────────

    @Test
    @DisplayName("El estado nace VIGENTE y se guarda como texto, no como posicion del enum")
    void guardar_persisteElEstadoComoTexto_yNaceVigente() {
        Prestamo prestamo = prestamoRepository.saveAndFlush(Prestamo.builder()
                .codigoLibro("BIB-0040")
                .rutUsuario(RUT)
                .fechaPrestamo(LocalDate.of(2026, 9, 1))
                .fechaVencimiento(LocalDate.of(2026, 9, 15))
                .build());

        String almacenado = jdbcTemplate.queryForObject(
                "SELECT estado FROM prestamos WHERE id = ?", String.class, prestamo.getId());

        assertEquals(EstadoPrestamo.VIGENTE, prestamo.getEstado());
        assertEquals("VIGENTE", almacenado);
    }
}
