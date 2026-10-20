package pe.pucp.tel05.taxi.taxista.web.dto;

import jakarta.validation.constraints.NotBlank;

public record FinalizarServicioRequest(@NotBlank String qrCodigo) {
}
