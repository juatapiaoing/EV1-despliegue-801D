package cl.jctapia.prestamos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cl.jctapia.prestamos.model.EstadoPrestamo;
import cl.jctapia.prestamos.model.Prestamo;

/**
 * Acceso a datos de prestamos.
 *
 * Todas las consultas se resuelven con derived queries de Spring Data: el
 * nombre del metodo describe el filtro y Spring genera el SQL. Se evita @Query
 * mientras el criterio siga siendo expresable asi, porque el nombre del metodo
 * se valida al levantar el contexto y una consulta escrita a mano no.
 */
@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    /** Historial de un socio, del prestamo mas reciente al mas antiguo. */
    List<Prestamo> findByRutUsuarioOrderByFechaPrestamoDesc(String rutUsuario);

    /** Indica si el ejemplar ya esta comprometido en un prestamo con ese estado. */
    boolean existsByCodigoLibroAndEstado(String codigoLibro, EstadoPrestamo estado);

    /**
     * Igual que el anterior, pero ignorando un registro concreto. Se usa al
     * actualizar: el propio prestamo que se esta editando no debe contarse a si
     * mismo como un conflicto de disponibilidad.
     */
    boolean existsByCodigoLibroAndEstadoAndIdNot(String codigoLibro, EstadoPrestamo estado, Long id);
}
