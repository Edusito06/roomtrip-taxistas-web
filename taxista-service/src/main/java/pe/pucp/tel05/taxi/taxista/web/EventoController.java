package pe.pucp.tel05.taxi.taxista.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.pucp.tel05.taxi.taxista.domain.EventoLog;
import pe.pucp.tel05.taxi.taxista.service.EventoService;

/** El Superadmin puede ver los logs de los eventos sucedidos (plan de proyecto). */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService eventos;

    public EventoController(EventoService eventos) {
        this.eventos = eventos;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<EventoLog> recientes() {
        return eventos.recientes();
    }
}
