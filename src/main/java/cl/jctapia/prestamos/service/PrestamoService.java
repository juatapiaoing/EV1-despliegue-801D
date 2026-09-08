package cl.jctapia.prestamos.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.jctapia.prestamos.dto.PrestamoRequest;
import cl.jctapia.prestamos.dto.PrestamoResponse;
import cl.jctapia.prestamos.exception.BusinessRuleException;
import cl.jctapia.prestamos.exception.EntityNotFoundException;
import cl.jctapia.prestamos.mapper.PrestamoMapper;
import cl.jctapia.prestamos.model.EstadoPrestamo;
import cl.jctapia.prestamos.model.Prestamo;
import cl.jctapia.prestamos.repository.PrestamoRepository;
import lombok.RequiredArgsConstructor;

/**
 * Reglas de negocio de los prestamos.
 *
 * Concentra las decisiones que no puede tomar ni el controlador ni la
 * validacion declarativa de los DTO:
 *
 *   1. La fecha de vencimiento debe ser posterior a la del prestamo.
 *   2. Un mismo ejemplar no puede estar en dos prestamos VIGENTE a la vez.
 *   3. Un prestamo nace siempre en estado VIGENTE, lo decida quien lo decida.
 *
 * El controlador queda reducido a traducir HTTP y el repositorio a SQL.
 */
@Service
@RequiredArgsConstructor
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final PrestamoMapper prestamoMapper;

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PrestamoResponse> findAll() {
        return prestamoMapper.toResponseList(prestamoRepository.findAll());
    }

    @Transactional(readOnly = true)
    public PrestamoResponse findById(Long id) {
        return prestamoMapper.toResponse(obtenerPrestamo(id));
    }

    @Transactional(readOnly = true)
    public List<PrestamoResponse> findByRutUsuario(String rutUsuario) {
        List<Prestamo> historial = prestamoRepository.findByRutUsuarioOrderByFechaPrestamoDesc(rutUsuario);

        // Un historial vacio no es un error: el socio simplemente nunca ha
        // pedido un libro. Se devuelve la lista vacia y no un 404.
        return prestamoMapper.toResponseList(historial);
    }

    /**
     * Prestamos VIGENTE cuya fecha de vencimiento ya paso.
     *
     * Un prestamo que vence hoy todavia no esta atrasado: el socio tiene el
     * dia completo para devolverlo, por eso se compara con "antes de hoy".
     * Los DEVUELTO y CANCELADO no cuentan aunque hayan vencido: ya no hay
     * ejemplar que reclamar.
     */
    @Transactional(readOnly = true)
    public List<PrestamoResponse> findAtrasados() {
        List<Prestamo> atrasados = prestamoRepository
                .findByEstadoAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(
                        EstadoPrestamo.VIGENTE, LocalDate.now());

        return prestamoMapper.toResponseList(atrasados);
    }

    // ─── Comandos ─────────────────────────────────────────────────────────────

    @Transactional
    public PrestamoResponse create(PrestamoRequest request) {
        validarRangoFechas(request);
        validarEjemplarDisponible(request.getCodigoLibro(), null);

        Prestamo prestamo = prestamoMapper.toEntity(request);
        prestamo.setEstado(EstadoPrestamo.VIGENTE);

        return prestamoMapper.toResponse(prestamoRepository.save(prestamo));
    }

    @Transactional
    public PrestamoResponse update(Long id, PrestamoRequest request) {
        Prestamo prestamo = obtenerPrestamo(id);

        validarRangoFechas(request);

        // Solo tiene sentido revisar disponibilidad si el ejemplar cambio: si es
        // el mismo codigo, el conflicto seria consigo mismo.
        if (!request.getCodigoLibro().equals(prestamo.getCodigoLibro())) {
            validarEjemplarDisponible(request.getCodigoLibro(), id);
        }

        prestamoMapper.updateEntity(request, prestamo);

        return prestamoMapper.toResponse(prestamoRepository.save(prestamo));
    }

    /**
     * Registra la devolucion de un ejemplar.
     *
     * Es la unica via para cerrar un prestamo: el PUT ignora estado y fecha de
     * devolucion a proposito. Solo un prestamo VIGENTE puede devolverse; uno
     * DEVUELTO o CANCELADO ya termino su ciclo y volver a cerrarlo seria un
     * error del cliente, no una operacion idempotente.
     */
    @Transactional
    public PrestamoResponse registrarDevolucion(Long id) {
        Prestamo prestamo = obtenerPrestamo(id);

        if (prestamo.getEstado() != EstadoPrestamo.VIGENTE) {
            throw new BusinessRuleException(String.format(
                    "El prestamo %d no esta vigente (estado actual: %s) y no admite devolucion",
                    id, prestamo.getEstado()));
        }

        prestamo.setFechaDevolucion(LocalDate.now());
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);

        return prestamoMapper.toResponse(prestamoRepository.save(prestamo));
    }

    /**
     * Elimina un prestamo que ya termino su ciclo.
     *
     * Un prestamo VIGENTE no se borra: el ejemplar sigue fuera de la biblioteca
     * y eliminar el registro haria perder su rastro (y liberaria el ejemplar
     * para un nuevo prestamo sin que nadie lo haya devuelto). Primero hay que
     * registrar la devolucion o cancelarlo.
     */
    @Transactional
    public void deleteById(Long id) {
        Prestamo prestamo = obtenerPrestamo(id);

        if (prestamo.getEstado() == EstadoPrestamo.VIGENTE) {
            throw new BusinessRuleException(String.format(
                    "El prestamo %d esta VIGENTE y no puede eliminarse: registre la devolucion o cancelelo primero",
                    id));
        }

        prestamoRepository.delete(prestamo);
    }

    // ─── Apoyo interno ────────────────────────────────────────────────────────

    private Prestamo obtenerPrestamo(Long id) {
        return prestamoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prestamos", "ID", id));
    }

    /**
     * Un prestamo que vence antes de empezar no es un error de formato sino una
     * incoherencia entre dos campos, por eso se valida aqui y no con una
     * anotacion en el DTO.
     */
    private void validarRangoFechas(PrestamoRequest request) {
        if (!request.getFechaVencimiento().isAfter(request.getFechaPrestamo())) {
            throw new BusinessRuleException(
                    "La fecha de vencimiento debe ser posterior a la fecha de prestamo");
        }
    }

    /**
     * Impide prestar dos veces el mismo ejemplar fisico.
     *
     * @param idExcluido prestamo que no debe considerarse en la comprobacion
     *                   (el que se esta editando); null al crear uno nuevo.
     */
    private void validarEjemplarDisponible(String codigoLibro, Long idExcluido) {
        boolean prestado = idExcluido == null
                ? prestamoRepository.existsByCodigoLibroAndEstado(codigoLibro, EstadoPrestamo.VIGENTE)
                : prestamoRepository.existsByCodigoLibroAndEstadoAndIdNot(codigoLibro, EstadoPrestamo.VIGENTE, idExcluido);

        if (prestado) {
            throw new BusinessRuleException(String.format(
                    "El ejemplar '%s' ya tiene un prestamo vigente y no puede volver a prestarse",
                    codigoLibro));
        }
    }
}
