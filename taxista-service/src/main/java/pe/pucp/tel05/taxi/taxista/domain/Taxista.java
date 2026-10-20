package pe.pucp.tel05.taxi.taxista.domain;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento raiz del subsistema. Cubre RF-WTX-002 (datos exigidos en el
 * autoregistro), RF-WTX-003/004 (habilitacion) y RF-WTX-007 (disponibilidad).
 *
 * <p>La contrasenia se guarda siempre como hash BCrypt, nunca en claro
 * (requerimiento de calidad del plan de proyecto).
 *
 * <p>valoracionPromedio es una copia de solo lectura que mantiene
 * calificacion-service; la fuente de verdad de las calificaciones vive alla.
 */
@Document(collection = "taxistas")
public class Taxista {

    @Id
    private String id;

    // --- Datos personales ---
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    @Indexed(unique = true)
    private String numeroDocumento;
    @Indexed(unique = true)
    private String correo;
    private LocalDate fechaNacimiento;
    private String telefono;
    private String domicilio;
    private String passwordHash;
    private String fotoUrl;

    // --- Datos del vehiculo ---
    private String vehiculoMarca;
    private String vehiculoModelo;
    private String vehiculoAnio;
    private String vehiculoColor;
    @Indexed(unique = true)
    private String placa;
    private String fotoVehiculoUrl;

    // --- Estado ---
    private EstadoHabilitacion estadoHabilitacion = EstadoHabilitacion.PENDIENTE;
    private EstadoDisponibilidad estadoDisponibilidad = EstadoDisponibilidad.NO_DISPONIBLE;
    private String motivoRechazo;
    private double valoracionPromedio;
    private long totalCalificaciones;

    // --- Ubicacion (se reporta solo durante un servicio activo) ---
    private Double ubicacionLatitud;
    private Double ubicacionLongitud;
    private Instant ubicacionFecha;

    // --- Auditoria (MOD-LOG) ---
    private Instant fechaRegistro = Instant.now();
    private Instant fechaActualizacion = Instant.now();
    private String decididoPor;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getDomicilio() {
        return domicilio;
    }

    public void setDomicilio(String domicilio) {
        this.domicilio = domicilio;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public String getVehiculoMarca() {
        return vehiculoMarca;
    }

    public void setVehiculoMarca(String vehiculoMarca) {
        this.vehiculoMarca = vehiculoMarca;
    }

    public String getVehiculoModelo() {
        return vehiculoModelo;
    }

    public void setVehiculoModelo(String vehiculoModelo) {
        this.vehiculoModelo = vehiculoModelo;
    }

    public String getVehiculoAnio() {
        return vehiculoAnio;
    }

    public void setVehiculoAnio(String vehiculoAnio) {
        this.vehiculoAnio = vehiculoAnio;
    }

    public String getVehiculoColor() {
        return vehiculoColor;
    }

    public void setVehiculoColor(String vehiculoColor) {
        this.vehiculoColor = vehiculoColor;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getFotoVehiculoUrl() {
        return fotoVehiculoUrl;
    }

    public void setFotoVehiculoUrl(String fotoVehiculoUrl) {
        this.fotoVehiculoUrl = fotoVehiculoUrl;
    }

    public EstadoHabilitacion getEstadoHabilitacion() {
        return estadoHabilitacion;
    }

    public void setEstadoHabilitacion(EstadoHabilitacion estadoHabilitacion) {
        this.estadoHabilitacion = estadoHabilitacion;
    }

    public EstadoDisponibilidad getEstadoDisponibilidad() {
        return estadoDisponibilidad;
    }

    public void setEstadoDisponibilidad(EstadoDisponibilidad estadoDisponibilidad) {
        this.estadoDisponibilidad = estadoDisponibilidad;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public double getValoracionPromedio() {
        return valoracionPromedio;
    }

    public void setValoracionPromedio(double valoracionPromedio) {
        this.valoracionPromedio = valoracionPromedio;
    }

    public long getTotalCalificaciones() {
        return totalCalificaciones;
    }

    public void setTotalCalificaciones(long totalCalificaciones) {
        this.totalCalificaciones = totalCalificaciones;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(Instant fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public String getDecididoPor() {
        return decididoPor;
    }

    public void setDecididoPor(String decididoPor) {
        this.decididoPor = decididoPor;
    }

    public Double getUbicacionLatitud() {
        return ubicacionLatitud;
    }

    public Double getUbicacionLongitud() {
        return ubicacionLongitud;
    }

    public Instant getUbicacionFecha() {
        return ubicacionFecha;
    }

    public void setUbicacion(Double latitud, Double longitud, Instant fecha) {
        this.ubicacionLatitud = latitud;
        this.ubicacionLongitud = longitud;
        this.ubicacionFecha = fecha;
    }
}
