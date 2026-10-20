package pe.pucp.tel05.taxi.taxista.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * RF-WTX-005: el propio taxista (o el Superadmin) actualiza su informacion.
 *
 * <p>A proposito NO incluye tipoDocumento ni numeroDocumento (identidad, no se
 * autoedita) ni estadoHabilitacion/estadoDisponibilidad/valoracion* (esos los
 * gestionan sus propios flujos: aprobacion, disponibilidad y calificaciones).
 * password es opcional: en blanco significa "no cambiarla", por eso no lleva
 * {@code @Size} aqui (se valida en el servicio solo cuando viene con datos).
 */
public record ActualizarPerfilRequest(
        @NotBlank(message = "Los nombres son obligatorios") String nombres,
        @NotBlank(message = "Los apellidos son obligatorios") String apellidos,
        @NotBlank(message = "El telefono es obligatorio") String telefono,
        @NotBlank(message = "El domicilio es obligatorio") String domicilio,
        @NotBlank @Email(message = "El correo no tiene un formato valido") String correo,
        String password,

        @NotBlank(message = "La marca del vehiculo es obligatoria") String vehiculoMarca,
        @NotBlank(message = "El modelo del vehiculo es obligatorio") String vehiculoModelo,
        @NotBlank(message = "El anio del vehiculo es obligatorio") String vehiculoAnio,
        @NotBlank(message = "El color del vehiculo es obligatorio") String vehiculoColor,
        @NotBlank(message = "La placa es obligatoria") String placa,
        @NotBlank(message = "La foto del taxista es obligatoria") String fotoUrl,
        @NotBlank(message = "La foto del vehiculo es obligatoria") String fotoVehiculoUrl) {
}
