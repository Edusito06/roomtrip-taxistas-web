package pe.pucp.tel05.taxi.portal.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SesionExpiradaFilterTest {

    private final SesionExpiradaFilter filtro = new SesionExpiradaFilter();

    private MockHttpServletResponse peticion(String ruta, String idSesion, boolean valida) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", ruta);
        if (idSesion != null) {
            req.setRequestedSessionId(idSesion);
            req.setRequestedSessionIdValid(valida);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(req, res, cadena);
        return res;
    }

    @Test
    @DisplayName("Cookie de sesion que el servidor ya no reconoce -> login con aviso de sesion expirada")
    void sesionExpiradaRedirigeConAviso() throws Exception {
        assertEquals("/login?expirada", peticion("/admin/taxistas", "vieja", false).getRedirectedUrl());
        assertEquals("/login?expirada", peticion("/mi-cuenta", "vieja", false).getRedirectedUrl());
    }

    @Test
    @DisplayName("Sesion vigente o sin cookie: el filtro no interviene")
    void sesionVigenteOSinCookiePasa() throws Exception {
        assertNull(peticion("/admin/taxistas", "activa", true).getRedirectedUrl());
        assertNull(peticion("/admin/taxistas", null, true).getRedirectedUrl());
    }

    @Test
    @DisplayName("Las paginas publicas nunca muestran el aviso, aunque la cookie sea vieja")
    void paginasPublicasNoSeTocan() throws Exception {
        assertNull(peticion("/login", "vieja", false).getRedirectedUrl());
        assertNull(peticion("/registro", "vieja", false).getRedirectedUrl());
    }
}
