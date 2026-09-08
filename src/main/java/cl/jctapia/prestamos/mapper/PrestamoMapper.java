package cl.jctapia.prestamos.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import cl.jctapia.prestamos.dto.PrestamoRequest;
import cl.jctapia.prestamos.dto.PrestamoResponse;
import cl.jctapia.prestamos.model.Prestamo;

/**
 * Conversion entre la entidad Prestamo y sus DTO.
 *
 * MapStruct genera la implementacion en tiempo de compilacion, asi que los
 * errores de mapeo aparecen al compilar y no en produccion. componentModel
 * "spring" hace que la clase generada sea un @Component inyectable.
 */
@Mapper(componentModel = "spring")
public interface PrestamoMapper {

    // El id lo asigna la base de datos; el estado y la fecha de devolucion los
    // controla el servicio. Dejarlos fuera del mapeo impide que un cliente
    // manipule el ciclo de vida del prestamo enviandolos en el JSON.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "fechaDevolucion", ignore = true)
    Prestamo toEntity(PrestamoRequest request);

    PrestamoResponse toResponse(Prestamo prestamo);

    List<PrestamoResponse> toResponseList(List<Prestamo> prestamos);

    /**
     * Copia los datos del request SOBRE la entidad ya recuperada de la base de
     * datos, en lugar de crear un objeto nuevo.
     *
     * Esto importa porque la instancia sigue siendo la misma que administra JPA:
     * al terminar la transaccion Hibernate emite un UPDATE y no un INSERT. Si se
     * construyera un Prestamo nuevo se perderian el id y el estado actual.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "fechaDevolucion", ignore = true)
    void updateEntity(PrestamoRequest request, @MappingTarget Prestamo prestamo);
}
