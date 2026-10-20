package pe.pucp.tel05.taxi.taxista.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento relevante del sistema (requerimiento de calidad: "el sistema deberá
 * registrar eventos relevantes"). Es solo de escritura y lectura: nunca se edita.
 */
@Document(collection = "eventos")
public class EventoLog {

    @Id
    private String id;
    @Indexed
    private Instant fecha = Instant.now();
    private String tipo;
    private String actor;
    private String sujetoId;
    private String detalle;

    public EventoLog() {
    }

    public EventoLog(String tipo, String actor, String sujetoId, String detalle) {
        this.tipo = tipo;
        this.actor = actor;
        this.sujetoId = sujetoId;
        this.detalle = detalle;
    }

    public String getId() { return id; }
    public Instant getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public String getActor() { return actor; }
    public String getSujetoId() { return sujetoId; }
    public String getDetalle() { return detalle; }
}
