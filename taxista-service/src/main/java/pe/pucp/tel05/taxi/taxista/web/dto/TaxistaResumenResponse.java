package pe.pucp.tel05.taxi.taxista.web.dto;

import pe.pucp.tel05.taxi.taxista.domain.Taxista;

/**
 * Vista reducida del taxista para la app movil: lo que el cliente necesita ver del taxista asignado
 * (nombre, foto, vehiculo, valoracion) y lo que la app necesita para saber si puede operar (estados).
 *
 * <p>A proposito NO incluye numero de documento, fecha de nacimiento ni domicilio: son datos
 * personales que la app no necesita para nada y no deben viajar a un celular ajeno.
 */
public record TaxistaResumenResponse(
        String id,
        String nombres,
        String apellidos,
        String correo,
        String telefono,
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

    public static TaxistaResumenResponse desde(Taxista t) {
        return new TaxistaResumenResponse(
                t.getId(),
                t.getNombres(),
                t.getApellidos(),
                t.getCorreo(),
                t.getTelefono(),
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
