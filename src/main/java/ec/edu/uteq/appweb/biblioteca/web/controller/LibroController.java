package ec.edu.uteq.appweb.biblioteca.web.controller;

import ec.edu.uteq.appweb.biblioteca.domain.Libro;
import ec.edu.uteq.appweb.biblioteca.integration.OpenLibraryClient;
import ec.edu.uteq.appweb.biblioteca.integration.OpenLibraryResponse;
import ec.edu.uteq.appweb.biblioteca.service.LibroService;
import ec.edu.uteq.appweb.biblioteca.web.dto.ApiResponse;
import ec.edu.uteq.appweb.biblioteca.web.dto.LibroEnriquecidoResponse;
import ec.edu.uteq.appweb.biblioteca.web.dto.LibroRequest;
import ec.edu.uteq.appweb.biblioteca.web.dto.LibroResponse;
import ec.edu.uteq.appweb.biblioteca.web.dto.PageMeta;
import ec.edu.uteq.appweb.biblioteca.web.mapper.LibroMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO-U4-1 RESUELTO: API REST del catalogo.
 *
 * Replica el patron de AutorController:
 *   - ruta base versionada, sustantivo en plural, sin verbos: /api/v1/libros
 *   - exito envuelto en ApiResponse; los errores los produce GlobalExceptionHandler
 *     como ProblemDetail (RFC 9457). Nunca se mezclan los dos formatos.
 *   - listado paginado con sus metadatos en el campo meta
 *   - creacion 201 Created + cabecera Location
 *   - borrado 204 No Content (logico: LibroService.desactivar)
 *   - escrituras protegidas por rol con @PreAuthorize
 *
 * Nota sobre @Transactional(readOnly = true) en los metodos de lectura:
 * Libro mantiene autor, editorial y categoria en FetchType.LAZY y el proyecto
 * corre con spring.jpa.open-in-view=false. Sin una transaccion viva en esta
 * capa, LibroMapper reventaria con LazyInitializationException al leer
 * libro.getAutor().getNombre(). AutorController no lo necesita porque Autor
 * no tiene asociaciones perezosas.
 */
@RestController
@RequestMapping("/api/v1/libros")
public class LibroController {

    private final LibroService servicio;
    private final LibroMapper mapper;
    private final OpenLibraryClient openLibrary;

    public LibroController(LibroService servicio, LibroMapper mapper, OpenLibraryClient openLibrary) {
        this.servicio = servicio;
        this.mapper = mapper;
        this.openLibrary = openLibrary;
    }

    /**
     * B1. GET /api/v1/libros?titulo=&categoriaId=&anioDesde=&page=&size=
     * Los tres filtros son opcionales y se delegan tal cual a LibroService.buscar.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<LibroResponse>> listar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Integer anioDesde,
            @PageableDefault(size = 20) Pageable paginacion) {

        Page<Libro> pagina = servicio.buscar(titulo, categoriaId, anioDesde, paginacion);
        List<LibroResponse> datos = pagina.getContent().stream().map(mapper::aRespuesta).toList();
        return ApiResponse.ok(datos, "Libros listados", PageMeta.de(pagina));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<LibroResponse> buscar(@PathVariable Long id) {
        return ApiResponse.ok(mapper.aRespuesta(servicio.buscarPorId(id)), "Libro encontrado");
    }

    /**
     * B2. POST /api/v1/libros
     * El cuerpo invalido lo convierte GlobalExceptionHandler en un 400 Problem
     * Details con el arreglo errors poblado: aqui no se escribe ese manejo.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<LibroResponse>> crear(@Valid @RequestBody LibroRequest solicitud) {
        Libro creado = servicio.crear(solicitud);
        LibroResponse cuerpo = mapper.aRespuesta(creado);
        return ResponseEntity
                .created(URI.create("/api/v1/libros/" + creado.getId()))
                .body(ApiResponse.ok(cuerpo, "Libro creado"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ApiResponse<LibroResponse> actualizar(@PathVariable Long id,
                                                 @Valid @RequestBody LibroRequest solicitud) {
        return ApiResponse.ok(mapper.aRespuesta(servicio.actualizar(id, solicitud)), "Libro actualizado");
    }

    /**
     * Borrado logico: la Unidad III fijo que el catalogo no elimina filas.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * B3. GET /api/v1/libros/{id}/enriquecido
     *
     * Si el proveedor no conoce el ISBN (404), OpenLibraryClient devuelve null y
     * este endpoint responde 200 con el libro local y los campos externos en null.
     * Si el proveedor falla de verdad (5xx, 4xx distinto de 404 o timeout), el
     * cliente lanza ServicioExternoException y el manejador global la traduce a 502.
     */
    @GetMapping("/{id}/enriquecido")
    @Transactional(readOnly = true)
    public ApiResponse<LibroEnriquecidoResponse> enriquecido(@PathVariable Long id) {
        Libro libro = servicio.buscarPorId(id);
        LibroResponse local = mapper.aRespuesta(libro);

        OpenLibraryResponse externo = openLibrary.consultarPorIsbn(libro.getIsbn());

        LibroEnriquecidoResponse cuerpo = (externo == null)
                ? new LibroEnriquecidoResponse(local, null, null, null, null)
                : new LibroEnriquecidoResponse(local,
                        externo.title(),
                        externo.urlPortada(),
                        externo.number_of_pages(),
                        externo.publish_date());

        String mensaje = (externo == null)
                ? "Libro sin metadatos externos disponibles"
                : "Libro enriquecido con Open Library";
        return ApiResponse.ok(cuerpo, mensaje);
    }
}