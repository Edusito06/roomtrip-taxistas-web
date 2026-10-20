package pe.pucp.tel05.taxi.taxista.web.dto;

import java.time.Instant;

import pe.pucp.tel05.taxi.taxista.domain.Taxista;

/** Ultima posicion conocida; latitud y longitud son null si aun no reporto ninguna. */
public record UbicacionResponse(
        String taxistaId,
        Double latitud,
        Double longitud,
        Instant actualizadaEn) {

    public static UbicacionResponse desde(Taxista t) {
        return new UbicacionResponse(t.getId(), t.getUbicacionLatitud(),
                t.getUbicacionLongitud(), t.getUbicacionFecha());
    }
}
