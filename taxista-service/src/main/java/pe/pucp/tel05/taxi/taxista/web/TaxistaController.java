package pe.pucp.tel05.taxi.taxista.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.service.EventoService;
import pe.pucp.tel05.taxi.taxista.service.TaxistaService;
import pe.pucp.tel05.taxi.taxista.web.dto.ActualizarPerfilRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.ActualizarValoracionRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.CambioDisponibilidadRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.DecisionAprobacionRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.RegistroTaxistaRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.TaxistaResponse;
import pe.pucp.tel05.taxi.taxista.web.dto.UbicacionRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.UbicacionResponse;

/**
 * API REST del dominio taxista (MOD-WTX + MOD-API).
 *
 * <p>El control de acceso por rol se declara con @PreAuthorize sobre cada
 * metodo: es lo que hace verificable el requerimiento de calidad
 * "restringir las operaciones segun el rol autenticado".
 */
@RestController
@RequestMapping("/api/taxistas")
public class TaxistaController {

    private final TaxistaService servicio;
    private final EventoService eventos;

    public TaxistaController(TaxistaService servicio, EventoService eventos) {
        this.servicio = servicio;
        this.eventos = eventos;
    }

    /** RF-WTX-001 y RF-WTX-002. Publico: es el autoregistro. */
    @PostMapping("/registro")
    public ResponseEntity<TaxistaResponse> registrar(@Valid @RequestBody RegistroTaxistaRequest req) {
        Taxista creado = servicio.registrar(req);
        eventos.registrar("TAXISTA_REGISTRADO", creado.getCorreo(), creado.getId(), "Autoregistro, placa " + creado.getPlaca());
        return ResponseEntity
                .created(URI.create("/api/taxistas/" + creado.getId()))
                .body(TaxistaResponse.desde(creado));
    }

    /** RF-WTX-004: bandeja de solicitudes pendientes. */
    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<TaxistaResponse> listarPendientes() {
        return servicio.listarPendientes().stream().map(TaxistaResponse::desde).toList();
    }

    /** RF-WTX-005: padron completo. */
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<TaxistaResponse> listarTodos() {
        return servicio.listarTodos().stream().map(TaxistaResponse::desde).toList();
    }

    /**
     * RF-API-001: unico endpoint que consume la app movil para asignar un taxi.
     * Devuelve solo APROBADO + DISPONIBLE.
     */
    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL')")
    public List<TaxistaResponse> listarDisponibles() {
        return servicio.listarDisponibles().stream().map(TaxistaResponse::desde).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL') or #id == authentication.name")
    public TaxistaResponse obtener(@PathVariable String id) {
        return TaxistaResponse.desde(servicio.obtener(id));
    }

    /** RF-WTX-004: aprobar o rechazar. Solo el Superadmin. */
    @PatchMapping("/{id}/aprobacion")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public TaxistaResponse decidir(@PathVariable String id,
                                   @Valid @RequestBody DecisionAprobacionRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        Taxista actualizado = servicio.decidirSolicitud(
                id, req.aprobado(), req.motivo(), jwt.getSubject());
        eventos.registrar(req.aprobado() ? "TAXISTA_APROBADO" : "TAXISTA_RECHAZADO",
                jwt.getSubject(), id, req.motivo());
        return TaxistaResponse.desde(actualizado);
    }

    /**
     * RF-WTX-007. Lo puede llamar el propio taxista (inicia o termina jornada)
     * o la app movil (marcarlo EN_SERVICIO al asignarle un traslado).
     */
    @PatchMapping("/{id}/disponibilidad")
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL') or #id == authentication.name")
    public TaxistaResponse cambiarDisponibilidad(@PathVariable String id,
                                                 @Valid @RequestBody CambioDisponibilidadRequest req,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Taxista t = servicio.cambiarDisponibilidad(id, req.estado());
        eventos.registrar("DISPONIBILIDAD_CAMBIADA", jwt.getSubject(), id, "Nuevo estado " + req.estado());
        return TaxistaResponse.desde(t);
    }

    /**
     * RF-WTX-005: el taxista edita su propia informacion (o el Superadmin,
     * la de cualquiera). Nunca cambia habilitacion, disponibilidad ni
     * valoracion: esos campos no estan en el DTO de entrada.
     */
    @PatchMapping("/{id}/perfil")
    @PreAuthorize("hasRole('SUPERADMIN') or #id == authentication.name")
    public TaxistaResponse actualizarPerfil(@PathVariable String id,
                                            @Valid @RequestBody ActualizarPerfilRequest req) {
        return TaxistaResponse.desde(servicio.actualizarPerfil(id, req));
    }

    /**
     * El taxista reporta su posicion durante un servicio activo. No se
     * registra como evento: es periodica y llenaria el log.
     */
    @PutMapping("/{id}/ubicacion")
    @PreAuthorize("hasRole('APP_MOVIL') or #id == authentication.name")
    public UbicacionResponse actualizarUbicacion(@PathVariable String id,
                                                 @Valid @RequestBody UbicacionRequest req) {
        return UbicacionResponse.desde(servicio.actualizarUbicacion(id, req.latitud(), req.longitud()));
    }

    /** La app movil muestra al cliente donde esta el taxista en un mapa. */
    @GetMapping("/{id}/ubicacion")
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL') or #id == authentication.name")
    public UbicacionResponse obtenerUbicacion(@PathVariable String id) {
        return UbicacionResponse.desde(servicio.obtener(id));
    }

    /**
     * Llamada interna de calificacion-service (RN-013): empuja el promedio ya
     * calculado alla, porque taxista-service no lee esa base de datos.
     * Solo la acepta un token con rol SERVICIO_INTERNO, emitido por
     * calificacion-service con el mismo secreto compartido.
     */
    @PatchMapping("/{id}/valoracion")
    @PreAuthorize("hasRole('SERVICIO_INTERNO')")
    public TaxistaResponse actualizarValoracion(@PathVariable String id,
                                                @Valid @RequestBody ActualizarValoracionRequest req) {
        return TaxistaResponse.desde(servicio.actualizarValoracion(id, req.promedio(), req.total()));
    }
}
