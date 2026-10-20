package pe.pucp.tel05.taxi.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Prueba el gateway de punta a punta contra un backend real en un
 * contenedor (WireMock), no contra un mock en memoria: valida que el borde
 * exige JWT, que enruta correctamente al servicio de dominio, y que el
 * circuit breaker abre y devuelve el 503 legible cuando el backend no
 * responde a tiempo (RF-TAX-018, RNF-FIA-001).
 *
 * <p>Se usa WireMock en lugar de levantar taxista-service real porque lo que
 * se quiere probar es el comportamiento del gateway (ruteo, seguridad,
 * resiliencia), no la logica de negocio del servicio de dominio, que ya
 * tiene sus propias pruebas en taxista-service.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final String JWT_SECRET = "prueba-secreto-de-al-menos-32-caracteres-1234";

    @Container
    static GenericContainer<?> backendStub =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        String stubUrl = "http://" + backendStub.getHost() + ":" + backendStub.getMappedPort(8080);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.servicios.taxista-url", () -> stubUrl);
        registry.add("app.servicios.calificacion-url", () -> stubUrl);

        // Circuito mas rapido que en produccion para que la prueba no tarde
        // los 10s reales: lo que importa es que abra, no cuanto tarda en abrir.
        registry.add("resilience4j.circuitbreaker.instances.taxistaCB.slidingWindowSize", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.taxistaCB.minimumNumberOfCalls", () -> "2");
        registry.add("resilience4j.timelimiter.instances.taxistaCB.timeoutDuration", () -> "1s");
    }

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void programarStub() {
        String stubUrl = "http://" + backendStub.getHost() + ":" + backendStub.getMappedPort(8080);
        WebTestClient admin = WebTestClient.bindToServer().baseUrl(stubUrl).build();

        // Ruta feliz: el gateway debe reenviar esto tal cual.
        admin.post().uri("/__admin/mappings")
                .bodyValue("""
                        {
                          "request": { "method": "GET", "url": "/api/taxistas/pendientes" },
                          "response": {
                            "status": 200,
                            "headers": { "Content-Type": "application/json" },
                            "jsonBody": [ { "id": "t1", "nombres": "Juan" } ]
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isCreated();

        // Backend lento a proposito: mas lento que el timeout del circuito,
        // para forzar la apertura sin depender de que el contenedor se caiga.
        admin.post().uri("/__admin/mappings")
                .bodyValue("""
                        {
                          "request": { "method": "GET", "url": "/api/taxistas/disponibles" },
                          "response": {
                            "status": 200,
                            "fixedDelayMilliseconds": 3000,
                            "jsonBody": []
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    @Order(1)
    void sinTokenDevuelve401() {
        webTestClient.get().uri("/api/taxistas/pendientes")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(2)
    void conTokenValidoEnrutaAlBackendYDevuelveSuRespuesta() {
        webTestClient.get().uri("/api/taxistas/pendientes")
                .header("Authorization", "Bearer " + token("SUPERADMIN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("t1")
                .jsonPath("$[0].nombres").isEqualTo("Juan");
    }

    /**
     * Ultima a proposito: abre el circuito de taxistaCB, que es compartido por
     * toda la ruta "/api/taxistas/**" (ver RouteConfig), asi que si corriera
     * antes contaminaria las pruebas anteriores con el circuito abierto.
     */
    @Test
    @Order(3)
    void backendLentoAbreElCircuitoYElGatewayResponde503ConMensajeLegible() {
        String token = token("APP_MOVIL");

        // Suficientes llamadas fallidas (timeout) para superar
        // minimumNumberOfCalls y que el circuito se abra.
        for (int i = 0; i < 3; i++) {
            webTestClient.get().uri("/api/taxistas/disponibles")
                    .header("Authorization", "Bearer " + token)
                    .exchange();
        }

        // Con el circuito abierto, el gateway responde al instante sin
        // volver a esperar el timeout de 1s configurado para la prueba.
        webTestClient.get().uri("/api/taxistas/disponibles")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICIO_NO_DISPONIBLE")
                .jsonPath("$.status").isEqualTo(503);
    }

    /**
     * La app movil tambien usa PATCH (estado, disponibilidad) y PUT (ubicacion):
     * con el circuito abierto deben recibir el mismo 503 legible y no un 405.
     * Depende de que la prueba anterior haya dejado el circuito abierto.
     */
    @Test
    @Order(4)
    void conElCircuitoAbiertoPatchYPutTambienRecibenElMensaje503() {
        String token = token("APP_MOVIL");

        webTestClient.patch().uri("/api/servicios/s1/estado")
                .header("Authorization", "Bearer " + token)
                .bodyValue("{\"estado\":\"EN_CAMINO\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICIO_NO_DISPONIBLE");

        webTestClient.put().uri("/api/taxistas/t1/ubicacion")
                .header("Authorization", "Bearer " + token)
                .bodyValue("{\"latitud\":-12.0,\"longitud\":-77.0}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    private String token(String rol) {
        try {
            Instant ahora = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("test-subject")
                    .claim("roles", List.of(rol))
                    .issueTime(Date.from(ahora))
                    .expirationTime(Date.from(ahora.plus(5, ChronoUnit.MINUTES)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", e);
        }
    }
}
