package pe.pucp.tel05.taxi.taxista.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * RF-WTX-002: la solicitud no se envia si falta alguno de los datos o
 * fotografias requeridos. Las anotaciones de validacion son lo que hace
 * verificable ese criterio de aceptacion.
 */
public record RegistroTaxistaRequest(
        @NotBlank(message = "Los nombres son obligatorios") String nombres,
        @NotBlank(message = "Los apellidos son obligatorios") String apellidos,
        @NotBlank(message = "El tipo de documento es obligatorio") String tipoDocumento,
        @NotBlank(message = "El numero de documento es obligatorio") String numeroDocumento,
        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser anterior a hoy") LocalDate fechaNacimiento,
        @NotBlank @Email(message = "El correo no tiene un formato valido") String correo,
        @NotBlank(message = "El telefono es obligatorio") String telefono,
        @NotBlank(message = "El domicilio es obligatorio") String domicilio,
        @NotBlank @Size(min = 8, message = "La contrasenia debe tener al menos 8 caracteres") String password,
        @NotBlank(message = "La foto del taxista es obligatoria") String fotoUrl,

        @NotBlank(message = "La marca del vehiculo es obligatoria") String vehiculoMarca,
        @NotBlank(message = "El modelo del vehiculo es obligatorio") String vehiculoModelo,
        @NotBlank(message = "El anio del vehiculo es obligatorio") String vehiculoAnio,
        @NotBlank(message = "El color del vehiculo es obligatorio") String vehiculoColor,
        @NotBlank(message = "La placa es obligatoria") String placa,
        @NotBlank(message = "La foto del vehiculo es obligatoria") String fotoVehiculoUrl) {
}
