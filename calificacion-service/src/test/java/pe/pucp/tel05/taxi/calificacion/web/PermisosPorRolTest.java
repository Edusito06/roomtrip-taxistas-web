package pe.pucp.tel05.taxi.calificacion.web;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.pucp.tel05.taxi.calificacion.domain.Calificacion;
import pe.pucp.tel05.taxi.calificacion.security.SecurityConfig;
import pe.pucp.tel05.taxi.calificacion.service.CalificacionService;
import pe.pucp.tel05.taxi.calificacion.web.dto.ValoracionResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Quien puede calificar y quien puede leer las calificaciones y la valoracion de un taxista.
 * "PERMITIDO" = la seguridad deja pasar (no 401 ni 403).
 */
@WebMvcTest(controllers = CalificacionController.class,
        properties = "app.jwt.secret=prueba-secreto-de-al-menos-32-caracteres-1234")
@Import(SecurityConfig.class)
class PermisosPorRolTest {

    private static final int PERMITIDO = -1;
    private static final String CALIFICACION =
            "{\"taxistaId\":\"t1\",\"servicioTaxiId\":\"s1\",\"puntuacion\":5,\"observacion\":\"ok\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CalificacionService servicio;

    @BeforeEach
    void simularNegocio() {
        when(servicio.registrar(anyString(), anyString(), anyInt(), any()))
                .thenReturn(new Calificacion("t1", "s1", 5, "ok"));
        when(servicio.valoracion(anyString())).thenReturn(new ValoracionResponse("t1", 4.5, 2));
        when(servicio.listarPorTaxista(anyString())).thenReturn(List.of());
    }

    static Stream<Arguments> casos() {
        HttpMethod GET = HttpMethod.GET, POST = HttpMethod.POST;
        String SA = "SUPERADMIN", APP = "APP_MOVIL", TX = "TAXISTA";
        return Stream.of(
                // Sin token
                Arguments.of(POST, "/api/calificaciones", CALIFICACION, null, null, 401),
                Arguments.of(GET, "/api/calificaciones/taxista/t1/valoracion", null, null, null, 401),
                // Solo la app movil califica (RF-TAX-017)
                Arguments.of(POST, "/api/calificaciones", CALIFICACION, APP, "app", PERMITIDO),
                Arguments.of(POST, "/api/calificaciones", CALIFICACION, SA, "a1", 403),
                Arguments.of(POST, "/api/calificaciones", CALIFICACION, TX, "t1", 403),
                // Valoracion promedio (RN-013): Superadmin, app movil o el propio taxista
                Arguments.of(GET, "/api/calificaciones/taxista/t1/valoracion", null, SA, "a1", PERMITIDO),
                Arguments.of(GET, "/api/calificaciones/taxista/t1/valoracion", null, APP, "app", PERMITIDO),
                Arguments.of(GET, "/api/calificaciones/taxista/t1/valoracion", null, TX, "t1", PERMITIDO),
                Arguments.of(GET, "/api/calificaciones/taxista/t1/valoracion", null, TX, "t2", 403),
                // Listado completo: Superadmin o el propio taxista; la app no
                Arguments.of(GET, "/api/calificaciones/taxista/t1", null, SA, "a1", PERMITIDO),
                Arguments.of(GET, "/api/calificaciones/taxista/t1", null, TX, "t1", PERMITIDO),
                Arguments.of(GET, "/api/calificaciones/taxista/t1", null, TX, "t2", 403),
                Arguments.of(GET, "/api/calificaciones/taxista/t1", null, APP, "app", 403));
    }

    @ParameterizedTest(name = "{0} {1} como {3} ({4}) -> {5}")
    @MethodSource("casos")
    void cadaRolSoloHaceLoQueLeCorresponde(HttpMethod metodo, String ruta, String cuerpo,
                                           String rol, String sujeto, int esperado) throws Exception {
        MockHttpServletRequestBuilder req = request(metodo, ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo == null ? "" : cuerpo);
        if (rol != null) {
            req = req.with(jwt().jwt(j -> j.subject(sujeto))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
        }

        int estado = mvc.perform(req).andReturn().getResponse().getStatus();

        if (esperado == PERMITIDO) {
            assertNotEquals(401, estado, "no debia pedir autenticacion");
            assertNotEquals(403, estado, "no debia negar el acceso");
        } else {
            assertEquals(esperado, estado);
        }
    }
}
