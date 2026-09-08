package cl.jctapia.prestamos.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa el prestamo de un ejemplar a un usuario de la
 * biblioteca.
 *
 * Se indexan rut_usuario, codigo_libro y estado porque son las tres columnas
 * sobre las que filtran las consultas del servicio (historial por usuario y
 * verificacion de disponibilidad del ejemplar).
 */
@Entity
@Table(
    name = "prestamos",
    indexes = {
        @Index(name = "idx_prestamos_rut_usuario", columnList = "rut_usuario"),
        @Index(name = "idx_prestamos_codigo_libro", columnList = "codigo_libro"),
        @Index(name = "idx_prestamos_estado", columnList = "estado")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Codigo interno del ejemplar prestado (no es el ISBN del titulo). */
    @Column(name = "codigo_libro", nullable = false, length = 20)
    private String codigoLibro;

    /** RUT del socio que retira el ejemplar, en formato 12345678-9. */
    @Column(name = "rut_usuario", nullable = false, length = 12)
    private String rutUsuario;

    @Column(name = "fecha_prestamo", nullable = false)
    private LocalDate fechaPrestamo;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    /** Queda null mientras el ejemplar no regrese a la biblioteca. */
    @Column(name = "fecha_devolucion")
    private LocalDate fechaDevolucion;

    // EnumType.STRING y no ORDINAL: guardar el nombre mantiene los datos
    // legibles y evita que agregar un valor al enum corrompa los registros
    // historicos, cosa que si ocurre al persistir la posicion numerica.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoPrestamo estado = EstadoPrestamo.VIGENTE;

    @Column(name = "observacion", length = 255)
    private String observacion;
}
