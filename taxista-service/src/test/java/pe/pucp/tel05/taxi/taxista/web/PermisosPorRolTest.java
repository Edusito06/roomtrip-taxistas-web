package pe.pucp.tel05.taxi.taxista.web;

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

import pe.pucp.tel05.taxi.taxista.domain.ServicioTaxi;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.security.SecurityConfig;
import pe.pucp.tel05.taxi.taxista.service.AuthService;
import pe.pucp.tel05.taxi.taxista.service.EventoService;
import pe.pucp.tel05.taxi.taxista.service.ServicioTaxiService;
import pe.pucp.tel05.taxi.taxista.service.TaxistaService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Requerimiento de calidad del plan de proyecto: "el sistema debera restringir las operaciones
 * segun el rol autenticado" y "un cliente solo podra acceder a sus propios ... servicios de taxi".
 *
 * <p>Cada fila es una llamada real a la cadena de seguridad y a los controladores (los servicios
 * de negocio estan simulados). "PERMITIDO" significa que la seguridad deja pasar (no 401 ni 403);
 * lo que importa aqui es quien entra y quien no, no el resultado del negocio.
 */
@WebMvcTest(controllers = {TaxistaController.class, ServicioTaxiController.class, EventoController.class,
        AuthController.class},
        properties = "app.jwt.secret=prueba-secreto-de-al-menos-32-caracteres-1234")
@Import({SecurityConfig.class, ManejadorErrores.class})
class PermisosPorRolTest {

    private static final int PERMITIDO = -1;

    private static final String VACIO = "{}";
    private static final String PERFIL = """
            {"nombres":"Juan","apellidos":"Perez","telefono":"987654321","domicilio":"Av. Larco 1",
             "correo":"j@x.pe","vehiculoMarca":"Toyota","vehiculoModelo":"Yaris","vehiculoAnio":"2020",
             "vehiculoColor":"Blanco","placa":"ABC-123","fotoUrl":"https://x/f.jpg","fotoVehiculoUrl":"https://x/v.jpg"}""";
    private static final String UBICACION = "{\"latitud\":-12.04,\"longitud\":-77.03}";
    private static final String SERVICIO = "{\"hotelId\":\"h1\",\"hotelNombre\":\"Hotel\",\"clienteId\":\"c1\",\"aeropuertoDestino\":\"Lima\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaxistaService taxistas;
    @MockitoBean
    private ServicioTaxiService servicios;
    @MockitoBean
    private EventoService eventos;
    @MockitoBean
    private AuthService auth;

    @BeforeEach
    void simularNegocio() {
        Taxista t = new Taxista();
        t.setId("t1");
        ServicioTaxi s = new ServicioTaxi();
        s.setId("s1");
        s.setTaxistaId("t1");

        when(taxistas.obtener(anyString())).thenReturn(t);
        when(taxistas.listarTodos()).thenReturn(List.of());
        when(taxistas.listarPendientes()).thenReturn(List.of());
        when(taxistas.listarDisponibles()).thenReturn(List.of());
        when(taxistas.decidirSolicitud(any(), anyBoolean(), any(), any())).thenReturn(t);
        when(taxistas.inhabilitar(any(), any())).thenReturn(t);
        when(taxistas.reactivar(any())).thenReturn(t);
        when(taxistas.cambiarDisponibilidad(any(), any())).thenReturn(t);
        when(taxistas.actualizarPerfil(any(), any())).thenReturn(t);
        when(taxistas.actualizarUbicacion(any(), anyDouble(), anyDouble())).thenReturn(t);
        when(taxistas.actualizarValoracion(any(), anyDouble(), anyLong())).thenReturn(t);

        when(servicios.solicitar(any(), any(), any(), any())).thenReturn(s);
        when(servicios.obtener(anyString())).thenReturn(s);
        when(servicios.recientes()).thenReturn(List.of());
        when(servicios.solicitados()).thenReturn(List.of());
        when(servicios.deTaxista(any())).thenReturn(List.of());
        when(servicios.aceptar(any(), any())).thenReturn(s);
        when(servicios.avanzar(any(), any(), any())).thenReturn(s);
        when(servicios.finalizar(any(), any(), any())).thenReturn(s);
        when(eventos.recientes()).thenReturn(List.of());
    }

    private static Arguments caso(HttpMethod metodo, String ruta, String cuerpo, String rol, String sujeto, int esperado) {
        return Arguments.of(metodo, ruta, cuerpo, rol, sujeto, esperado);
    }

    static Stream<Arguments> casos() {
        HttpMethod GET = HttpMethod.GET, POST = HttpMethod.POST, PATCH = HttpMethod.PATCH, PUT = HttpMethod.PUT;
        String SA = "SUPERADMIN", APP = "APP_MOVIL", TX = "TAXISTA", INT = "SERVICIO_INTERNO";
        return Stream.of(
                // --- Sin token: todo lo privado responde 401
                caso(GET, "/api/taxistas", null, null, null, 401),
                caso(GET, "/api/servicios/solicitados", null, null, null, 401),
                caso(GET, "/api/eventos", null, null, null, 401),
                // --- Publicos
                caso(POST, "/api/taxistas/registro", VACIO, null, null, PERMITIDO),
                caso(POST, "/api/auth/login", VACIO, null, null, PERMITIDO),

                // --- Eventos: solo el Superadmin
                caso(GET, "/api/eventos", null, SA, "a1", PERMITIDO),
                caso(GET, "/api/eventos", null, TX, "t1", 403),
                caso(GET, "/api/eventos", null, APP, "app", 403),

                // --- Padron y bandeja: solo el Superadmin
                caso(GET, "/api/taxistas", null, SA, "a1", PERMITIDO),
                caso(GET, "/api/taxistas", null, APP, "app", 403),
                caso(GET, "/api/taxistas", null, TX, "t1", 403),
                caso(GET, "/api/taxistas/pendientes", null, SA, "a1", PERMITIDO),
                caso(GET, "/api/taxistas/pendientes", null, TX, "t1", 403),

                // --- Disponibles: Superadmin y app movil, nunca un taxista
                caso(GET, "/api/taxistas/disponibles", null, APP, "app", PERMITIDO),
                caso(GET, "/api/taxistas/disponibles", null, SA, "a1", PERMITIDO),
                caso(GET, "/api/taxistas/disponibles", null, TX, "t1", 403),

                // --- Ver un taxista: el propio, la app o el Superadmin; no otro taxista
                caso(GET, "/api/taxistas/t1", null, TX, "t1", PERMITIDO),
                caso(GET, "/api/taxistas/t1", null, TX, "t2", 403),
                caso(GET, "/api/taxistas/t1", null, APP, "app", PERMITIDO),

                // --- Aprobar/rechazar y activar/desactivar: solo el Superadmin
                caso(PATCH, "/api/taxistas/t1/aprobacion", "{\"aprobado\":true}", SA, "a1", PERMITIDO),
                caso(PATCH, "/api/taxistas/t1/aprobacion", "{\"aprobado\":true}", APP, "app", 403),
                caso(PATCH, "/api/taxistas/t1/aprobacion", "{\"aprobado\":true}", TX, "t1", 403),
                caso(PATCH, "/api/taxistas/t1/habilitacion", "{\"activo\":false,\"motivo\":\"x\"}", SA, "a1", PERMITIDO),
                caso(PATCH, "/api/taxistas/t1/habilitacion", "{\"activo\":false,\"motivo\":\"x\"}", APP, "app", 403),
                caso(PATCH, "/api/taxistas/t1/habilitacion", "{\"activo\":true}", TX, "t1", 403),

                // --- Disponibilidad: el propio taxista, la app o el Superadmin
                caso(PATCH, "/api/taxistas/t1/disponibilidad", "{\"estado\":\"DISPONIBLE\"}", TX, "t1", PERMITIDO),
                caso(PATCH, "/api/taxistas/t1/disponibilidad", "{\"estado\":\"DISPONIBLE\"}", TX, "t2", 403),
                caso(PATCH, "/api/taxistas/t1/disponibilidad", "{\"estado\":\"DISPONIBLE\"}", APP, "app", PERMITIDO),

                // --- Perfil: el propio taxista o el Superadmin; la app no
                caso(PATCH, "/api/taxistas/t1/perfil", PERFIL, TX, "t1", PERMITIDO),
                caso(PATCH, "/api/taxistas/t1/perfil", PERFIL, TX, "t2", 403),
                caso(PATCH, "/api/taxistas/t1/perfil", PERFIL, SA, "a1", PERMITIDO),
                caso(PATCH, "/api/taxistas/t1/perfil", PERFIL, APP, "app", 403),

                // --- Ubicacion: reporta el propio taxista o la app (no el Superadmin); lee quien corresponde
                caso(PUT, "/api/taxistas/t1/ubicacion", UBICACION, TX, "t1", PERMITIDO),
                caso(PUT, "/api/taxistas/t1/ubicacion", UBICACION, TX, "t2", 403),
                caso(PUT, "/api/taxistas/t1/ubicacion", UBICACION, APP, "app", PERMITIDO),
                caso(PUT, "/api/taxistas/t1/ubicacion", UBICACION, SA, "a1", 403),
                caso(GET, "/api/taxistas/t1/ubicacion", null, TX, "t1", PERMITIDO),
                caso(GET, "/api/taxistas/t1/ubicacion", null, TX, "t2", 403),
                caso(GET, "/api/taxistas/t1/ubicacion", null, APP, "app", PERMITIDO),

                // --- Valoracion: solo la llamada interna de calificacion-service
                caso(PATCH, "/api/taxistas/t1/valoracion", "{\"promedio\":4.5,\"total\":3}", INT, "cal", PERMITIDO),
                caso(PATCH, "/api/taxistas/t1/valoracion", "{\"promedio\":4.5,\"total\":3}", SA, "a1", 403),
                caso(PATCH, "/api/taxistas/t1/valoracion", "{\"promedio\":4.5,\"total\":3}", APP, "app", 403),

                // --- Servicios de taxi
                caso(POST, "/api/servicios", SERVICIO, APP, "app", PERMITIDO),
                caso(POST, "/api/servicios", SERVICIO, SA, "a1", PERMITIDO),
                caso(POST, "/api/servicios", SERVICIO, TX, "t1", 403),
                caso(GET, "/api/servicios", null, SA, "a1", PERMITIDO),
                caso(GET, "/api/servicios", null, APP, "app", 403),
                caso(GET, "/api/servicios", null, TX, "t1", 403),
                caso(GET, "/api/servicios/solicitados", null, TX, "t1", PERMITIDO),
                caso(GET, "/api/servicios/solicitados", null, APP, "app", PERMITIDO),
                caso(GET, "/api/servicios/mios", null, TX, "t1", PERMITIDO),
                caso(GET, "/api/servicios/mios", null, APP, "app", 403),
                caso(GET, "/api/servicios/mios", null, SA, "a1", 403),
                caso(GET, "/api/servicios/s1", null, APP, "app", PERMITIDO),
                caso(GET, "/api/servicios/s1", null, TX, "t1", PERMITIDO),      // el asignado
                caso(GET, "/api/servicios/s1", null, TX, "t2", 403),            // otro taxista
                caso(POST, "/api/servicios/s1/aceptar", null, TX, "t1", PERMITIDO),
                caso(POST, "/api/servicios/s1/aceptar", null, APP, "app", 403),
                caso(POST, "/api/servicios/s1/aceptar", null, SA, "a1", 403),
                caso(PATCH, "/api/servicios/s1/estado", "{\"estado\":\"EN_CAMINO\"}", TX, "t1", PERMITIDO),
                caso(PATCH, "/api/servicios/s1/estado", "{\"estado\":\"EN_CAMINO\"}", APP, "app", 403),
                caso(POST, "/api/servicios/s1/finalizar", "{\"qrCodigo\":\"x\"}", TX, "t1", PERMITIDO),
                caso(POST, "/api/servicios/s1/finalizar", "{\"qrCodigo\":\"x\"}", APP, "app", 403));
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
