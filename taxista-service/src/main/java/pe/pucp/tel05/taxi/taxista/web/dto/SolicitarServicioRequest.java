package pe.pucp.tel05.taxi.taxista.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SolicitarServicioRequest(
        @NotBlank String hotelId,
        @NotBlank String hotelNombre,
        @NotBlank String clienteId,
        @NotBlank String aeropuertoDestino) {
}
