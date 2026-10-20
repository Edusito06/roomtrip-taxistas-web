package pe.pucp.tel05.taxi.taxista.web.dto;

import jakarta.validation.constraints.NotNull;

import pe.pucp.tel05.taxi.taxista.domain.EstadoServicio;

public record AvanzarServicioRequest(@NotNull EstadoServicio estado) {
}
