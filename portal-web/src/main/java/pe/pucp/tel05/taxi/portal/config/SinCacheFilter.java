package pe.pucp.tel05.taxi.portal.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Las paginas dinamicas (paneles, mi cuenta, formularios) llevan datos de la sesion, asi que
 * el navegador no debe guardarlas: sin esto, tras cerrar sesion la flecha "atras" muestra la
 * pagina privada desde la cache. {@code no-store} tambien desactiva la cache de ida y vuelta
 * (bfcache) de Chrome y Safari.
 *
 * <p>Los recursos estaticos (/css, /js, /img) quedan fuera a proposito: no llevan datos y ya
 * van versionados por hash, asi que conviene que se cacheen.
 */
@Component
public class SinCacheFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/img/")
                || ruta.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        chain.doFilter(request, response);
    }
}
