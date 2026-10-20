package pe.pucp.tel05.taxi.portal.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Distingue "nunca inicio sesion" de "su sesion expiro". Si a una pagina privada llega una
 * cookie de sesion que el servidor ya no reconoce (vencio por inactividad o se cerro), manda al
 * login con ?expirada para mostrar el aviso. Sin cookie, el controlador redirige al login normal.
 */
@Component
public class SesionExpiradaFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !(ruta.startsWith("/admin") || ruta.startsWith("/mi-cuenta"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
            response.sendRedirect(request.getContextPath() + "/login?expirada");
            return;
        }
        chain.doFilter(request, response);
    }
}
