package cl.jctapia.prestamos.dto;

import java.time.LocalDate;

import org.springframework.hateoas.RepresentationModel;

import com.fasterxml.jackson.annotation.JsonFormat;

import cl.jctapia.prestamos.model.EstadoPrestamo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Representacion de un prestamo hacia el cliente.
 *
 * Extiende RepresentationModel para que Jackson agregue el bloque "_links" que
 * el controlador construye con HATEOAS: asi el consumidor descubre las
 * operaciones disponibles sin tener las URLs escritas a mano.
 *
 * callSuper = false evita que Lombok mezcle el equals/hashCode de la clase
 * padre, que compara la lista de enlaces y no los datos del prestamo.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PrestamoResponse extends RepresentationModel<PrestamoResponse> {

    private Long id;
    private String codigoLibro;
    private String rutUsuario;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaPrestamo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaVencimiento;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaDevolucion;

    private EstadoPrestamo estado;
    private String observacion;
}
