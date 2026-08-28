package ec.edu.uteq.appweb.biblioteca.integration;
import ec.edu.uteq.appweb.biblioteca.config.CacheConfig;
import ec.edu.uteq.appweb.biblioteca.exception.ServicioExternoException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenLibraryClient {

    private final RestClient restClient;

    public OpenLibraryClient(RestClient restClientExterno) {
        this.restClient = restClientExterno;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_OPENLIBRARY, key = "#isbn", unless = "#result == null")
    public OpenLibraryResponse consultarPorIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        try {
            return restClient.get()
                    .uri("/isbn/{isbn}.json", isbn)
                    .retrieve()
                    // El 404 se ataja antes de intentar deserializar el cuerpo:
                    // Open Library responde HTML en ese caso y Jackson fallaria.
                    .onStatus(estado -> estado.value() == 404,
                            (peticion, respuesta) -> {
                                throw new IsbnNoPublicadoException();
                            })
                    .body(OpenLibraryResponse.class);

        } catch (IsbnNoPublicadoException ex) {
            return null;

        } catch (RestClientResponseException ex) {
            // 4xx distinto de 404 y 5xx
            throw new ServicioExternoException(
                    "Open Library respondio " + ex.getStatusCode().value() + " para el ISBN " + isbn, ex);

        } catch (ResourceAccessException ex) {
            // timeout de conexion o de lectura, DNS caido, conexion rechazada
            throw new ServicioExternoException(
                    "No se pudo contactar a Open Library para el ISBN " + isbn, ex);

        } catch (RestClientException ex) {
            // cuerpo ilegible u otro fallo del cliente HTTP
            throw new ServicioExternoException(
                    "Respuesta invalida de Open Library para el ISBN " + isbn, ex);
        }
    }


    private static final class IsbnNoPublicadoException extends RuntimeException {

        private IsbnNoPublicadoException() {
            super(null, null, false, false);
        }
    }
}