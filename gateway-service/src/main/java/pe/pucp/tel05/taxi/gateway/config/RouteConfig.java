package pe.pucp.tel05.taxi.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Rutas del gateway definidas en Java en lugar de YAML.
 *
 * <p>Se hace asi a proposito: el prefijo de propiedades de Spring Cloud Gateway
 * cambio entre versiones (spring.cloud.gateway.routes ->
 * spring.cloud.gateway.server.webflux.routes), y una ruta mal prefijada se
 * ignora en silencio. La API de RouteLocatorBuilder es estable y falla al
 * compilar si algo no cuadra.
 */
@Configuration
public class RouteConfig {

    private final String taxistaServiceUrl;
    private final String calificacionServiceUrl;

    public RouteConfig(
            @Value("${app.servicios.taxista-url}") String taxistaServiceUrl,
            @Value("${app.servicios.calificacion-url}") String calificacionServiceUrl) {
        this.taxistaServiceUrl = taxistaServiceUrl;
        this.calificacionServiceUrl = calificacionServiceUrl;
    }

    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder) {
        return builder.routes()
                // Dominio taxista: autoregistro, aprobacion, datos, disponibilidad
                .route("taxista-service", r -> r
                        .path("/api/taxistas/**", "/api/auth/**", "/api/servicios/**", "/api/eventos/**")
                        .filters(f -> f.circuitBreaker(cb -> cb
                                .setName("taxistaCB")
                                .setFallbackUri("forward:/fallback/taxistas")))
                        .uri(normalizar(taxistaServiceUrl)))

                // Dominio calificacion: puntajes y valoracion promedio
                .route("calificacion-service", r -> r
                        .path("/api/calificaciones/**")
                        .filters(f -> f.circuitBreaker(cb -> cb
                                .setName("calificacionCB")
                                .setFallbackUri("forward:/fallback/calificaciones")))
                        .uri(normalizar(calificacionServiceUrl)))
                .build();
    }

    private String normalizar(String url) {
        return UriComponentsBuilder.fromUriString(url).build().toUriString();
    }
}
