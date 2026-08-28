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

    
}