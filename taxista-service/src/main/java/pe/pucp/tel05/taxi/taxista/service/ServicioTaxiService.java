package pe.pucp.tel05.taxi.taxista.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import pe.pucp.tel05.taxi.taxista.domain.EstadoDisponibilidad;
import pe.pucp.tel05.taxi.taxista.domain.EstadoHabilitacion;
import pe.pucp.tel05.taxi.taxista.domain.EstadoServicio;
import pe.pucp.tel05.taxi.taxista.domain.ServicioTaxi;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.repository.ServicioTaxiRepository;
import pe.pucp.tel05.taxi.taxista.repository.TaxistaRepository;
import pe.pucp.tel05.taxi.taxista.service.TaxistaService.ReglaNegocioException;
import pe.pucp.tel05.taxi.taxista.service.TaxistaService.RecursoNoEncontradoException;

/**
 * Ciclo de vida del servicio de taxi: SOLICITADO -> ASIGNADO -> EN_CAMINO ->
 * EN_TRASLADO -> FINALIZADO. Al aceptar un pedido el taxista queda asignado y
 * el pedido deja de estar disponible para los demas. FINALIZADO solo se
 * registra al validar el codigo QR que el hotel le envio al cliente.
 */
@Service
public class ServicioTaxiService {

    private final ServicioTaxiRepository servicios;
    private final TaxistaRepository taxistas;

    public ServicioTaxiService(ServicioTaxiRepository servicios, TaxistaRepository taxistas) {
        this.servicios = servicios;
        this.taxistas = taxistas;
    }

    public ServicioTaxi solicitar(String hotelId, String hotelNombre, String clienteId, String aeropuerto) {
        ServicioTaxi s = new ServicioTaxi();
        s.setHotelId(hotelId);
        s.setHotelNombre(hotelNombre);
        s.setClienteId(clienteId);
        s.setAeropuertoDestino(aeropuerto);
        s.setQrCodigo(UUID.randomUUID().toString());
        return servicios.save(s);
    }

    public List<ServicioTaxi> solicitados() {
        return servicios.findByEstadoOrderByFechaSolicitudAsc(EstadoServicio.SOLICITADO);
    }

    public List<ServicioTaxi> deTaxista(String taxistaId) {
        return servicios.findByTaxistaIdOrderByFechaSolicitudDesc(taxistaId);
    }

    public ServicioTaxi obtener(String id) {
        return servicios.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el servicio " + id));
    }

    /** El taxista toma el pedido: pasa a ASIGNADO y el taxista a EN_SERVICIO. */
    public ServicioTaxi aceptar(String id, String taxistaId) {
        Taxista t = taxistas.findById(taxistaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el taxista " + taxistaId));
        if (t.getEstadoHabilitacion() != EstadoHabilitacion.APROBADO
                || t.getEstadoDisponibilidad() != EstadoDisponibilidad.DISPONIBLE) {
            throw new ReglaNegocioException("Solo un taxista aprobado y disponible puede aceptar pedidos");
        }

        ServicioTaxi s = obtener(id);
        if (s.getEstado() != EstadoServicio.SOLICITADO) {
            throw new ReglaNegocioException("El pedido ya no esta disponible");
        }

        s.setEstado(EstadoServicio.ASIGNADO);
        s.setTaxistaId(taxistaId);
        s.setFechaAsignacion(Instant.now());
        s.setFechaActualizacion(Instant.now());
        try {
            s = servicios.save(s);
        } catch (OptimisticLockingFailureException e) {
            throw new ReglaNegocioException("El pedido ya fue tomado por otro taxista");
        }

        t.setEstadoDisponibilidad(EstadoDisponibilidad.EN_SERVICIO);
        t.setFechaActualizacion(Instant.now());
        taxistas.save(t);
        return s;
    }

    /** ASIGNADO -> EN_CAMINO -> EN_TRASLADO, solo por el taxista asignado. */
    public ServicioTaxi avanzar(String id, String taxistaId, EstadoServicio nuevo) {
        ServicioTaxi s = delTaxistaAsignado(id, taxistaId);
        boolean valido = (s.getEstado() == EstadoServicio.ASIGNADO && nuevo == EstadoServicio.EN_CAMINO)
                || (s.getEstado() == EstadoServicio.EN_CAMINO && nuevo == EstadoServicio.EN_TRASLADO);
        if (!valido) {
            throw new ReglaNegocioException(
                    "Transicion invalida: " + s.getEstado() + " -> " + nuevo
                            + ". FINALIZADO solo se registra validando el QR");
        }
        s.setEstado(nuevo);
        s.setFechaActualizacion(Instant.now());
        return servicios.save(s);
    }

    /** EN_TRASLADO -> FINALIZADO validando el QR del cliente; libera al taxista. */
    public ServicioTaxi finalizar(String id, String taxistaId, String qrCodigo) {
        ServicioTaxi s = delTaxistaAsignado(id, taxistaId);
        if (s.getEstado() != EstadoServicio.EN_TRASLADO) {
            throw new ReglaNegocioException("Solo se puede finalizar un servicio EN_TRASLADO");
        }
        if (qrCodigo == null || !qrCodigo.equals(s.getQrCodigo())) {
            throw new ReglaNegocioException("El codigo QR no corresponde a este servicio");
        }
        s.setEstado(EstadoServicio.FINALIZADO);
        s.setFechaFinalizacion(Instant.now());
        s.setFechaActualizacion(Instant.now());
        ServicioTaxi guardado = servicios.save(s);

        taxistas.findById(taxistaId).ifPresent(t -> {
            t.setEstadoDisponibilidad(EstadoDisponibilidad.DISPONIBLE);
            t.setFechaActualizacion(Instant.now());
            taxistas.save(t);
        });
        return guardado;
    }

    private ServicioTaxi delTaxistaAsignado(String id, String taxistaId) {
        ServicioTaxi s = obtener(id);
        if (!taxistaId.equals(s.getTaxistaId())) {
            throw new AccessDeniedException("El servicio esta asignado a otro taxista");
        }
        return s;
    }
}
