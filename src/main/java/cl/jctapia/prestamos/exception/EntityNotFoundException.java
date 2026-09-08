package cl.jctapia.prestamos.exception;

/**
 * Se lanza cuando el recurso solicitado no existe. El manejador global la
 * traduce a un HTTP 404.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entidad, String campo, Object valor) {
        super(String.format("No se encontraron registros en %s con el %s '%s'",
                entidad, campo, valor != null ? valor.toString() : "N/A"));
    }
}
