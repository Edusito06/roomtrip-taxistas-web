package pe.pucp.tel05.taxi.taxista.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Desactivar (activo=false, con motivo obligatorio) o reactivar (activo=true) a un taxista
 * ya aprobado. Solo el Superadmin.
 */
public record CambioHabilitacionRequest(
        @NotNull(message = "Indica si el taxista queda activo o desactivado") Boolean activo,
        String motivo) {
}
