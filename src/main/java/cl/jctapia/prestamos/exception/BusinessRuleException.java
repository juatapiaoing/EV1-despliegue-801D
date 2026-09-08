package cl.jctapia.prestamos.exception;

/**
 * Se lanza cuando la peticion es sintacticamente valida pero rompe una regla
 * del negocio (por ejemplo, prestar un ejemplar que ya esta prestado).
 *
 * Se separa de las excepciones de validacion porque el significado para el
 * cliente es distinto: no debe corregir el formato del JSON, sino el estado
 * del sistema. El manejador global la traduce a un HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String mensaje) {
        super(mensaje);
    }
}
