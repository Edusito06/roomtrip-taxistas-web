package pe.pucp.tel05.taxi.taxista.web.dto;

import pe.pucp.tel05.taxi.taxista.domain.Taxista;

/**
 * Proyeccion de salida. Nunca expone passwordHash: el documento de Mongo
 * no se serializa directo hacia la API a proposito.
 */
public record TaxistaResponse(
        String id,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        java.time.LocalDate fechaNacimiento,
        String correo,
        String telefono,
        String domicilio,
        String fotoUrl,
        String vehiculoMarca,
        String vehiculoModelo,
        String vehiculoAnio,
        String vehiculoColor,
        String placa,
        String fotoVehiculoUrl,
        String estadoHabilitacion,
        String estadoDisponibilidad,
        double valoracionPromedio,
        long totalCalificaciones) {

    public static TaxistaResponse desde(Taxista t) {
        return new TaxistaResponse(
                t.getId(),
                t.getNombres(),
                t.getApellidos(),
                t.getTipoDocumento(),
                t.getNumeroDocumento(),
                t.getFechaNacimiento(),
                t.getCorreo(),
                t.getTelefono(),
                t.getDomicilio(),
                t.getFotoUrl(),
                t.getVehiculoMarca(),
                t.getVehiculoModelo(),
                t.getVehiculoAnio(),
                t.getVehiculoColor(),
                t.getPlaca(),
                t.getFotoVehiculoUrl(),
                t.getEstadoHabilitacion().name(),
                t.getEstadoDisponibilidad().name(),
                t.getValoracionPromedio(),
                t.getTotalCalificaciones());
    }
}
