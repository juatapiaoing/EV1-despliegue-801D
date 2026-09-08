package cl.jctapia.prestamos.exception;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduce las excepciones de la aplicacion a respuestas HTTP coherentes.
 *
 * Centralizar el manejo de errores en un @RestControllerAdvice deja los
 * controladores limpios de bloques try/catch y garantiza que dos endpoints
 * distintos nunca devuelvan el mismo problema con formatos diferentes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Construye el envelope comun para no repetir el builder en cada handler. */
    private ResponseEntity<ApiError> build(HttpStatus status, String mensaje,
                                           List<String> detalles, HttpServletRequest request) {
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(mensaje)
                .errors(detalles)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    // ─── 404 NOT FOUND ────────────────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    // ─── 409 CONFLICT ─────────────────────────────────────────────────────────

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null, request);
    }

    // ─── 400 BAD REQUEST ──────────────────────────────────────────────────────

    /**
     * Falla la validacion declarativa de @Valid. Se devuelve la lista completa
     * de campos incorrectos y no solo el primero, para que el cliente pueda
     * corregir el formulario en un unico intento.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
                .toList();

        return build(HttpStatus.BAD_REQUEST, "La solicitud contiene campos invalidos",
                detalles, request);
    }

    /** JSON malformado o una fecha que no respeta el patron yyyy-MM-dd. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la peticion no se pudo interpretar. Revise el formato del JSON y de las fechas (yyyy-MM-dd)",
                null, request);
    }

    /** Un path variable con tipo incorrecto, por ejemplo /prestamos/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                String.format("El parametro '%s' tiene un valor invalido: '%s'",
                        ex.getName(), ex.getValue()),
                null, request);
    }

    // ─── 500 INTERNAL SERVER ERROR ────────────────────────────────────────────

    /**
     * Red de seguridad para cualquier fallo no previsto. El detalle tecnico se
     * registra en el log del servidor y NO se devuelve al cliente, para no
     * filtrar rutas de clases ni estructura interna de la base de datos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrio un error inesperado en el servidor", null, request);
    }
}
