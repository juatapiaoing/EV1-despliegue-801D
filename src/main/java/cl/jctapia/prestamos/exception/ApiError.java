package cl.jctapia.prestamos.exception;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Envelope unico de error de la API.
 *
 * Todas las respuestas fallidas (400, 404, 409, 500) comparten esta forma, de
 * modo que el cliente pueda parsear los errores con un solo modelo en lugar de
 * adivinar el formato segun el codigo HTTP.
 */
@Data
@Builder
public class ApiError {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;

    /** Detalle campo a campo. Solo se llena en errores de validacion (400). */
    private List<String> errors;

    /** URI que origino la peticion fallida, util para rastrear en los logs. */
    private String path;
}
