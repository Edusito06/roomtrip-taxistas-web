package pe.pucp.tel05.taxi.taxista.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un pedido de taxi creado por la app movil al hacer el checkout.
 *
 * <p>@Version da bloqueo optimista: si dos taxistas aceptan el mismo pedido a
 * la vez, el segundo guardado falla y el pedido queda solo con el primero.
 */
@Document(collection = "servicios")
public class ServicioTaxi {

    @Id
    private String id;
    @Version
    private Long version;

    private EstadoServicio estado = EstadoServicio.SOLICITADO;
    private String hotelId;
    private String hotelNombre;
    private String clienteId;
    private String aeropuertoDestino;
    @Indexed
    private String taxistaId;
    /** Valor que el hotel le envia al cliente como QR; el taxista lo valida al llegar. */
    private String qrCodigo;

    private Instant fechaSolicitud = Instant.now();
    private Instant fechaAsignacion;
    private Instant fechaFinalizacion;
    private Instant fechaActualizacion = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public EstadoServicio getEstado() { return estado; }
    public void setEstado(EstadoServicio estado) { this.estado = estado; }
    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public String getHotelNombre() { return hotelNombre; }
    public void setHotelNombre(String hotelNombre) { this.hotelNombre = hotelNombre; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public String getAeropuertoDestino() { return aeropuertoDestino; }
    public void setAeropuertoDestino(String aeropuertoDestino) { this.aeropuertoDestino = aeropuertoDestino; }
    public String getTaxistaId() { return taxistaId; }
    public void setTaxistaId(String taxistaId) { this.taxistaId = taxistaId; }
    public String getQrCodigo() { return qrCodigo; }
    public void setQrCodigo(String qrCodigo) { this.qrCodigo = qrCodigo; }
    public Instant getFechaSolicitud() { return fechaSolicitud; }
    public Instant getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(Instant fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }
    public Instant getFechaFinalizacion() { return fechaFinalizacion; }
    public void setFechaFinalizacion(Instant fechaFinalizacion) { this.fechaFinalizacion = fechaFinalizacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
