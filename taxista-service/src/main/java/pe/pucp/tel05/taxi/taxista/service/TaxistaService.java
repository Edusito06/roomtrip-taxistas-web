package pe.pucp.tel05.taxi.taxista.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import pe.pucp.tel05.taxi.taxista.domain.EstadoDisponibilidad;
import pe.pucp.tel05.taxi.taxista.domain.EstadoHabilitacion;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.repository.TaxistaRepository;
import pe.pucp.tel05.taxi.taxista.web.dto.ActualizarPerfilRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.RegistroTaxistaRequest;

/**
 * Reglas de negocio del ciclo de vida del taxista.
 *
 * <p>Toda la logica vive aqui y no en el controlador, para que sea testeable
 * sin levantar el contexto web ni Mongo.
 */
@Service
public class TaxistaService {

    private static final Logger log = LoggerFactory.getLogger(TaxistaService.class);

    private final TaxistaRepository repositorio;
    private final PasswordEncoder passwordEncoder;

    public TaxistaService(TaxistaRepository repositorio, PasswordEncoder passwordEncoder) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * RF-WTX-001 y RF-WTX-003: autoregistro que queda en estado PENDIENTE.
     *
     * @throws ReglaNegocioException si el correo, documento o placa ya existen
     */
    public Taxista registrar(RegistroTaxistaRequest req) {
        if (repositorio.existsByCorreo(req.correo())) {
            throw new ReglaNegocioException("Ya existe un taxista registrado con ese correo");
        }
        if (repositorio.existsByNumeroDocumento(req.numeroDocumento())) {
            throw new ReglaNegocioException("Ya existe un taxista registrado con ese numero de documento");
        }
        if (repositorio.existsByPlaca(req.placa())) {
            throw new ReglaNegocioException("Ya existe un vehiculo registrado con esa placa");
        }

        Taxista t = new Taxista();
        t.setNombres(req.nombres());
        t.setApellidos(req.apellidos());
        t.setTipoDocumento(req.tipoDocumento());
        t.setNumeroDocumento(req.numeroDocumento());
        t.setCorreo(req.correo().toLowerCase());
        t.setFechaNacimiento(req.fechaNacimiento());
        t.setTelefono(req.telefono());
        t.setDomicilio(req.domicilio());
        // Requerimiento de calidad: nunca en texto plano.
        t.setPasswordHash(passwordEncoder.encode(req.password()));
        t.setFotoUrl(req.fotoUrl());
        t.setVehiculoMarca(req.vehiculoMarca());
        t.setVehiculoModelo(req.vehiculoModelo());
        t.setVehiculoAnio(req.vehiculoAnio());
        t.setVehiculoColor(req.vehiculoColor());
        t.setPlaca(req.placa().toUpperCase());
        t.setFotoVehiculoUrl(req.fotoVehiculoUrl());
        t.setEstadoHabilitacion(EstadoHabilitacion.PENDIENTE);
        t.setEstadoDisponibilidad(EstadoDisponibilidad.NO_DISPONIBLE);

        Taxista guardado = repositorio.save(t);
        log.info("Nueva solicitud de taxista id={} documento={}",
                guardado.getId(), guardado.getNumeroDocumento());
        return guardado;
    }

    /** RF-WTX-004: bandeja de solicitudes del Superadmin. */
    public List<Taxista> listarPendientes() {
        return repositorio.findByEstadoHabilitacion(EstadoHabilitacion.PENDIENTE);
    }

    public List<Taxista> listarTodos() {
        return repositorio.findAll();
    }

    public Taxista obtener(String id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el taxista " + id));
    }

    /**
     * RF-WTX-004: aprobar o rechazar. Solo aplica sobre solicitudes PENDIENTES;
     * reprocesar una ya decidida es un error de negocio, no una operacion idempotente.
     */
    public Taxista decidirSolicitud(String id, boolean aprobado, String motivo, String decididoPor) {
        Taxista t = obtener(id);

        if (t.getEstadoHabilitacion() != EstadoHabilitacion.PENDIENTE) {
            throw new ReglaNegocioException(
                    "La solicitud ya fue procesada (estado actual: " + t.getEstadoHabilitacion() + ")");
        }

        if (aprobado) {
            t.setEstadoHabilitacion(EstadoHabilitacion.APROBADO);
            t.setMotivoRechazo(null);
            // Aprobado no significa disponible: el taxista activa su disponibilidad
            // cuando empieza su jornada.
            t.setEstadoDisponibilidad(EstadoDisponibilidad.NO_DISPONIBLE);
        } else {
            t.setEstadoHabilitacion(EstadoHabilitacion.RECHAZADO);
            t.setMotivoRechazo(motivo);
            t.setEstadoDisponibilidad(EstadoDisponibilidad.NO_DISPONIBLE);
        }

        t.setDecididoPor(decididoPor);
        t.setFechaActualizacion(Instant.now());

        log.info("Solicitud id={} {} por {}", id, aprobado ? "APROBADA" : "RECHAZADA", decididoPor);
        return repositorio.save(t);
    }

    /**
     * RF-WTX-007. Un taxista que no esta APROBADO no puede declararse disponible:
     * es la regla que impide que un PENDIENTE preste servicios (RF-WTX-003).
     */
    public Taxista cambiarDisponibilidad(String id, EstadoDisponibilidad estado) {
        Taxista t = obtener(id);

        if (t.getEstadoHabilitacion() != EstadoHabilitacion.APROBADO
                && estado != EstadoDisponibilidad.NO_DISPONIBLE) {
            throw new ReglaNegocioException(
                    "Un taxista no aprobado no puede pasar a " + estado);
        }

        t.setEstadoDisponibilidad(estado);
        t.setFechaActualizacion(Instant.now());
        return repositorio.save(t);
    }

    /**
     * Ubicacion periodica del taxista. Solo se acepta durante un servicio
     * activo (EN_SERVICIO): fuera de uno no hay nada que rastrear.
     */
    public Taxista actualizarUbicacion(String id, double latitud, double longitud) {
        Taxista t = obtener(id);
        if (t.getEstadoDisponibilidad() != EstadoDisponibilidad.EN_SERVICIO) {
            throw new ReglaNegocioException("La ubicacion solo se registra durante un servicio activo");
        }
        t.setUbicacion(latitud, longitud, Instant.now());
        return repositorio.save(t);
    }

    /** RF-API-001: los unicos taxistas asignables por la app movil. */
    public List<Taxista> listarDisponibles() {
        return repositorio.findByEstadoHabilitacionAndEstadoDisponibilidad(
                EstadoHabilitacion.APROBADO, EstadoDisponibilidad.DISPONIBLE);
    }

    /**
     * Copia local de la valoracion que mantiene calificacion-service.
     * Se acepta que este dato sea eventualmente consistente: la fuente de
     * verdad de las calificaciones es el otro microservicio.
     */
    public Taxista actualizarValoracion(String id, double promedio, long total) {
        Taxista t = obtener(id);
        t.setValoracionPromedio(promedio);
        t.setTotalCalificaciones(total);
        t.setFechaActualizacion(Instant.now());
        return repositorio.save(t);
    }

    public Optional<Taxista> buscarPorCorreo(String correo) {
        return repositorio.findByCorreo(correo.toLowerCase());
    }

    /**
     * RF-WTX-005: el taxista edita su propia informacion. Nunca toca
     * estadoHabilitacion, estadoDisponibilidad, valoracion* ni el documento de
     * identidad: esos campos los gobiernan otros flujos (aprobacion,
     * disponibilidad, calificaciones, registro), no este.
     *
     * <p>El correo y la placa se revalidan como unicos igual que en el
     * registro, pero excluyendo al propio taxista (si no cambio ese campo,
     * "ya existe" seria un falso positivo contra si mismo).
     */
    public Taxista actualizarPerfil(String id, ActualizarPerfilRequest req) {
        Taxista t = obtener(id);

        String correoNuevo = req.correo().toLowerCase();
        if (!correoNuevo.equals(t.getCorreo()) && repositorio.existsByCorreo(correoNuevo)) {
            throw new ReglaNegocioException("Ya existe un taxista registrado con ese correo");
        }
        String placaNueva = req.placa().toUpperCase();
        if (!placaNueva.equals(t.getPlaca()) && repositorio.existsByPlaca(placaNueva)) {
            throw new ReglaNegocioException("Ya existe un vehiculo registrado con esa placa");
        }

        t.setNombres(req.nombres());
        t.setApellidos(req.apellidos());
        t.setTelefono(req.telefono());
        t.setDomicilio(req.domicilio());
        t.setCorreo(correoNuevo);
        // En blanco = no cambiarla. Si viene con datos, se exige el mismo
        // minimo que en el registro y se hashea, nunca en texto plano.
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 8) {
                throw new ReglaNegocioException("La contrasenia debe tener al menos 8 caracteres");
            }
            t.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        t.setVehiculoMarca(req.vehiculoMarca());
        t.setVehiculoModelo(req.vehiculoModelo());
        t.setVehiculoAnio(req.vehiculoAnio());
        t.setVehiculoColor(req.vehiculoColor());
        t.setPlaca(placaNueva);
        t.setFotoUrl(req.fotoUrl());
        t.setFotoVehiculoUrl(req.fotoVehiculoUrl());
        t.setFechaActualizacion(Instant.now());

        Taxista guardado = repositorio.save(t);
        log.info("Perfil actualizado id={}", id);
        return guardado;
    }

    /** Error de negocio esperado: se traduce a HTTP 409. */
    public static class ReglaNegocioException extends RuntimeException {
        public ReglaNegocioException(String mensaje) {
            super(mensaje);
        }
    }

    /** Se traduce a HTTP 404. */
    public static class RecursoNoEncontradoException extends RuntimeException {
        public RecursoNoEncontradoException(String mensaje) {
            super(mensaje);
        }
    }
}
