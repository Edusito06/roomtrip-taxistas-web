package pe.pucp.tel05.taxi.taxista.web.dto;

import java.time.Instant;

import pe.pucp.tel05.taxi.taxista.domain.ServicioTaxi;

/**
 * qrCodigo solo se incluye para la app movil y el Superadmin: el taxista no
 * debe verlo, porque es lo que el cliente le muestra al llegar.
 */
public record ServicioResponse(
        String id,
        String estado,
        String hotelId,
        String hotelNombre,
        String clienteId,
        String aeropuertoDestino,
        String taxistaId,
        String qrCodigo,
        Instant fechaSolicitud,
        Instant fechaAsignacion,
        Instant fechaFinalizacion) {

    public static ServicioResponse desde(ServicioTaxi s, boolean incluirQr) {
        return new ServicioResponse(s.getId(), s.getEstado().name(), s.getHotelId(), s.getHotelNombre(),
                s.getClienteId(), s.getAeropuertoDestino(), s.getTaxistaId(),
                incluirQr ? s.getQrCodigo() : null,
                s.getFechaSolicitud(), s.getFechaAsignacion(), s.getFechaFinalizacion());
    }
}
