package pe.pucp.tel05.taxi.portal.client;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP del portal contra el gateway.
 *
 * <p>Convierte los errores del API en una excepcion propia con el mensaje que
 * devolvio el servicio, para poder mostrarlo tal cual en la pantalla en lugar
 * de un stacktrace.
 */
@Component
public class TaxiApiClient {

    private final RestClient restClient;

    public TaxiApiClient(@Value("${app.gateway-url}") String gatewayUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(gatewayUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Map<String, Object> login(String correo, String password) {
        return ejecutar(() -> restClient.post()
                .uri("/api/auth/login")
                .body(Map.of("correo", correo, "password", password))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    public Map<String, Object> registrarTaxista(Map<String, Object> solicitud) {
        return ejecutar(() -> restClient.post()
                .uri("/api/taxistas/registro")
                .body(solicitud)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    public List<Map<String, Object>> listarPendientes(String token) {
        return ejecutar(() -> restClient.get()
                .uri("/api/taxistas/pendientes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() { }));
    }

    public List<Map<String, Object>> listarTodos(String token) {
        return ejecutar(() -> restClient.get()
                .uri("/api/taxistas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() { }));
    }

    /** Servicios de taxi recientes (Superadmin). */
    public List<Map<String, Object>> listarServicios(String token) {
        return ejecutar(() -> restClient.get()
                .uri("/api/servicios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() { }));
    }

    /** Ultima ubicacion reportada por un taxista. */
    public Map<String, Object> ubicacionTaxista(String token, String id) {
        return ejecutar(() -> restClient.get()
                .uri("/api/taxistas/{id}/ubicacion", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** Log de eventos para el Superadmin. */
    public List<Map<String, Object>> listarEventos(String token) {
        return ejecutar(() -> restClient.get()
                .uri("/api/eventos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() { }));
    }

    public void decidirSolicitud(String token, String id, boolean aprobado, String motivo) {
        ejecutar(() -> restClient.patch()
                .uri("/api/taxistas/{id}/aprobacion", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(motivo == null
                        ? Map.of("aprobado", aprobado)
                        : Map.of("aprobado", aprobado, "motivo", motivo))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** RF-WTX-005: el propio taxista consultando su estado de solicitud. */
    public Map<String, Object> obtenerTaxista(String token, String id) {
        return ejecutar(() -> restClient.get()
                .uri("/api/taxistas/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** RF-WTX-007: el propio taxista activa o desactiva su disponibilidad. */
    public void cambiarDisponibilidad(String token, String id, String estado) {
        ejecutar(() -> restClient.patch()
                .uri("/api/taxistas/{id}/disponibilidad", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("estado", estado))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    /** RF-WTX-005: el propio taxista edita su informacion de contacto y vehiculo. */
    public void actualizarPerfil(String token, String id, Map<String, Object> datos) {
        ejecutar(() -> restClient.patch()
                .uri("/api/taxistas/{id}/perfil", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(datos)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }));
    }

    private <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            throw new ApiException(extraerMensaje(e), e.getStatusCode().value());
        } catch (Exception e) {
            // El gateway caido o el circuito abierto llegan por aca.
            throw new ApiException(
                    "No se pudo contactar el servicio de gestion de taxistas. Intentalo nuevamente.", 503);
        }
    }

    private String extraerMensaje(RestClientResponseException e) {
        try {
            String cuerpo = e.getResponseBodyAsString();
            int i = cuerpo.indexOf("\"mensaje\"");
            if (i >= 0) {
                int inicio = cuerpo.indexOf('"', cuerpo.indexOf(':', i)) + 1;
                int fin = cuerpo.indexOf('"', inicio);
                if (inicio > 0 && fin > inicio) {
                    return cuerpo.substring(inicio, fin);
                }
            }
        } catch (RuntimeException ignored) {
            // Si el cuerpo no es el JSON esperado, caemos al mensaje generico.
        }
        return "La operacion no pudo completarse (codigo " + e.getStatusCode().value() + ")";
    }

    /** Error de API ya traducido a algo que se le puede mostrar al usuario. */
    public static class ApiException extends RuntimeException {
        private final int status;

        public ApiException(String mensaje, int status) {
            super(mensaje);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
