package cl.jctapia.prestamos.model;

/**
 * Ciclo de vida de un prestamo.
 *
 * Se modela como enum y no como String libre para que la base de datos y la
 * API compartan exactamente el mismo conjunto de valores validos, evitando
 * estados escritos a mano ("vigente", "Vigente", "VIGENT") que romperian las
 * consultas por estado.
 *
 * El atraso NO es un estado persistido: se deriva comparando la fecha de
 * vencimiento con la fecha actual mientras el prestamo siga VIGENTE. Guardarlo
 * obligaria a un proceso que actualice la tabla cada dia.
 */
public enum EstadoPrestamo {

    /** El libro esta en poder del usuario y todavia no se devuelve. */
    VIGENTE,

    /** El libro ya fue devuelto a la biblioteca. */
    DEVUELTO,

    /** El prestamo se anulo antes de entregar el libro (error de registro). */
    CANCELADO
}
