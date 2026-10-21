package pe.pucp.tel05.taxi.portal.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Un archivo mas grande que el limite (5 MB por foto, ver application.yml) llegaba como un 413
 * crudo. Ahora vuelve al formulario con un mensaje claro.
 */
@ControllerAdvice
public class ManejadorSubidas {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoMuyGrande(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Cada foto debe pesar como maximo 5 MB.");
        String ruta = request.getRequestURI();
        return "redirect:" + (ruta.startsWith("/mi-cuenta") ? "/mi-cuenta/editar" : "/registro");
    }
}
