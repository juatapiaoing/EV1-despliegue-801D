package cl.jctapia.prestamos.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.List;

import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.jctapia.prestamos.dto.PrestamoRequest;
import cl.jctapia.prestamos.dto.PrestamoResponse;
import cl.jctapia.prestamos.service.PrestamoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API REST de prestamos.
 *
 * El controlador no contiene logica de negocio: valida la entrada con @Valid,
 * delega en PrestamoService y traduce el resultado a codigos HTTP. Los errores
 * los transforma GlobalExceptionHandler.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/prestamos")
@Tag(name = "Prestamos", description = "Gestion de prestamos de ejemplares de la biblioteca")
public class PrestamoController {

    private final PrestamoService prestamoService;

    // ─── HATEOAS ──────────────────────────────────────────────────────────────

    /**
     * Agrega a la respuesta los enlaces de navegacion del recurso.
     *
     * linkTo(methodOn(...)) deriva la URL del propio controlador, de modo que si
     * manana cambia el @RequestMapping los enlaces siguen siendo correctos sin
     * tocar este metodo.
     */
    private PrestamoResponse addLinks(PrestamoResponse prestamo) {
        Long id = prestamo.getId();

        prestamo.add(linkTo(methodOn(PrestamoController.class).findById(id)).withSelfRel());

        prestamo.add(linkTo(methodOn(PrestamoController.class).update(id, null))
                .withRel("update").withTitle("PUT - Actualizar prestamo"));

        prestamo.add(linkTo(methodOn(PrestamoController.class).deleteById(id))
                .withRel("delete").withTitle("DELETE - Eliminar prestamo"));

        prestamo.add(linkTo(methodOn(PrestamoController.class).findByRutUsuario(prestamo.getRutUsuario()))
                .withRel("historial-usuario").withTitle("GET - Historial del socio"));

        prestamo.add(linkTo(methodOn(PrestamoController.class).findAll())
                .withRel("all").withTitle("GET - Listado de prestamos"));

        return prestamo;
    }

    // ─── Endpoints ────────────────────────────────────────────────────────────

    @Operation(summary = "Listar prestamos",
               description = "Retorna todos los prestamos registrados en el sistema")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrestamoResponse.class))))
    })
    @GetMapping
    public ResponseEntity<CollectionModel<PrestamoResponse>> findAll() {
        List<PrestamoResponse> prestamos = prestamoService.findAll();
        prestamos.forEach(this::addLinks);

        return ResponseEntity.ok(CollectionModel.of(
                prestamos,
                linkTo(methodOn(PrestamoController.class).findAll()).withSelfRel()));
    }

    @Operation(summary = "Obtener prestamo por ID",
               description = "Retorna un prestamo segun su identificador unico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Prestamo encontrado",
            content = @Content(schema = @Schema(implementation = PrestamoResponse.class))),
        @ApiResponse(responseCode = "404", description = "El prestamo no existe", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponse> findById(
            @Parameter(description = "ID del prestamo", required = true, example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(addLinks(prestamoService.findById(id)));
    }

    @Operation(summary = "Historial de un socio",
               description = "Retorna los prestamos de un usuario ordenados del mas reciente al mas antiguo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial obtenido correctamente",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PrestamoResponse.class))))
    })
    @GetMapping("/usuario/{rut}")
    public ResponseEntity<CollectionModel<PrestamoResponse>> findByRutUsuario(
            @Parameter(description = "RUT del socio, sin puntos y con guion", required = true, example = "18345678-9")
            @PathVariable String rut) {
        List<PrestamoResponse> historial = prestamoService.findByRutUsuario(rut);
        historial.forEach(this::addLinks);

        return ResponseEntity.ok(CollectionModel.of(
                historial,
                linkTo(methodOn(PrestamoController.class).findByRutUsuario(rut)).withSelfRel()));
    }

    @Operation(summary = "Registrar un prestamo",
               description = "Crea un prestamo en estado VIGENTE para un ejemplar disponible")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Prestamo registrado correctamente",
            content = @Content(schema = @Schema(implementation = PrestamoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos", content = @Content),
        @ApiResponse(responseCode = "409", description = "El ejemplar ya tiene un prestamo vigente o las fechas son incoherentes",
            content = @Content)
    })
    @PostMapping
    public ResponseEntity<PrestamoResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Datos del prestamo a registrar", required = true,
                content = @Content(schema = @Schema(implementation = PrestamoRequest.class)))
            @Valid @RequestBody PrestamoRequest request) {
        PrestamoResponse creado = addLinks(prestamoService.create(request));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Actualizar un prestamo",
               description = "Modifica los datos de un prestamo existente. El estado y la fecha de devolucion no se alteran por esta via")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Prestamo actualizado correctamente",
            content = @Content(schema = @Schema(implementation = PrestamoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos", content = @Content),
        @ApiResponse(responseCode = "404", description = "El prestamo no existe", content = @Content),
        @ApiResponse(responseCode = "409", description = "El ejemplar ya tiene un prestamo vigente o las fechas son incoherentes",
            content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<PrestamoResponse> update(
            @Parameter(description = "ID del prestamo a actualizar", required = true, example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Nuevos datos del prestamo", required = true,
                content = @Content(schema = @Schema(implementation = PrestamoRequest.class)))
            @Valid @RequestBody PrestamoRequest request) {
        return ResponseEntity.ok(addLinks(prestamoService.update(id, request)));
    }

    @Operation(summary = "Eliminar un prestamo",
               description = "Elimina definitivamente un prestamo del sistema")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Prestamo eliminado correctamente", content = @Content),
        @ApiResponse(responseCode = "404", description = "El prestamo no existe", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(
            @Parameter(description = "ID del prestamo a eliminar", required = true, example = "1")
            @PathVariable Long id) {
        prestamoService.deleteById(id);

        return ResponseEntity.noContent().build();
    }
}
