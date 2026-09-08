package cl.jctapia.prestamos.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos que el cliente envia para registrar o modificar un prestamo.
 *
 * Solo contiene los campos que el usuario puede decidir. El estado y la fecha
 * de devolucion quedan fuera a proposito: los administra el servicio, de modo
 * que nadie pueda marcar un prestamo como DEVUELTO desde un PUT.
 *
 * Aqui se valida el FORMATO de los datos. La coherencia entre campos
 * (vencimiento posterior al prestamo, ejemplar disponible) es una regla de
 * negocio y vive en PrestamoService.
 */
@Data
public class PrestamoRequest {

    @NotBlank(message = "El codigo del libro es obligatorio")
    @Pattern(
        regexp = "^[A-Z0-9-]{4,20}$",
        message = "El codigo del libro solo admite mayusculas, digitos y guiones (4 a 20 caracteres)"
    )
    private String codigoLibro;

    @NotBlank(message = "El RUT del usuario es obligatorio")
    @Pattern(
        regexp = "^[0-9]{7,8}-[0-9kK]$",
        message = "El RUT debe tener formato 12345678-9, sin puntos y con guion"
    )
    private String rutUsuario;

    @NotNull(message = "La fecha de prestamo es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaPrestamo;

    @NotNull(message = "La fecha de vencimiento es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaVencimiento;

    @Size(max = 255, message = "La observacion no puede superar los 255 caracteres")
    private String observacion;
}
