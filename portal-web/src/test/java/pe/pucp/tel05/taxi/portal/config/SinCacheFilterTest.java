package pe.pucp.tel05.taxi.portal.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinCacheFilterTest {

    private final SinCacheFilter filtro = new SinCacheFilter();

    private MockHttpServletResponse peticion(String ruta) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest("GET", ruta), respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test
    @DisplayName("Las paginas privadas llevan no-store: tras cerrar sesion, 'atras' no las muestra")
    void paginasDinamicasNoSeGuardan() throws Exception {
        for (String ruta : new String[] {"/admin/taxistas", "/mi-cuenta", "/login", "/registro"}) {
            MockHttpServletResponse r = peticion(ruta);
            assertTrue(r.getHeader("Cache-Control").contains("no-store"), ruta);
        }
    }

    @Test
    @DisplayName("Los recursos estaticos si se pueden cachear")
    void estaticosNoSeTocan() throws Exception {
        assertNull(peticion("/css/app-abc123.css").getHeader("Cache-Control"));
        assertNull(peticion("/js/app-abc123.js").getHeader("Cache-Control"));
    }
}
