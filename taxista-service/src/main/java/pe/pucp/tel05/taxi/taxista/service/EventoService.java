package pe.pucp.tel05.taxi.taxista.service;

import java.util.List;

import org.springframework.stereotype.Service;

import pe.pucp.tel05.taxi.taxista.domain.EventoLog;
import pe.pucp.tel05.taxi.taxista.repository.EventoLogRepository;

/** Registro de eventos relevantes para la auditoria del Superadmin. */
@Service
public class EventoService {

    private final EventoLogRepository repositorio;

    public EventoService(EventoLogRepository repositorio) {
        this.repositorio = repositorio;
    }

    public void registrar(String tipo, String actor, String sujetoId, String detalle) {
        repositorio.save(new EventoLog(tipo, actor, sujetoId, detalle));
    }

    public List<EventoLog> recientes() {
        return repositorio.findTop200ByOrderByFechaDesc();
    }
}
