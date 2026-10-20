package pe.pucp.tel05.taxi.taxista.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.pucp.tel05.taxi.taxista.domain.ServicioTaxi;
import pe.pucp.tel05.taxi.taxista.service.EventoService;
import pe.pucp.tel05.taxi.taxista.service.ServicioTaxiService;
import pe.pucp.tel05.taxi.taxista.web.dto.AvanzarServicioRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.FinalizarServicioRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.ServicioResponse;
import pe.pucp.tel05.taxi.taxista.web.dto.SolicitarServicioRequest;

/**
 * Servicios de taxi hotel -> aeropuerto. La app movil los crea y consulta su
 * estado; el taxista los acepta, los avanza y los finaliza con el QR.
 */
@RestController
@RequestMapping("/api/servicios")
public class ServicioTaxiController {

    private final ServicioTaxiService servicio;
    private final EventoService eventos;

    public ServicioTaxiController(ServicioTaxiService servicio, EventoService eventos) {
        this.servicio = servicio;
        this.eventos = eventos;
    }

    /** La app movil crea el pedido al hacer el checkout con taxi. */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL')")
    public ResponseEntity<ServicioResponse> solicitar(@Valid @RequestBody SolicitarServicioRequest req,
                                                      @AuthenticationPrincipal Jwt jwt) {
        ServicioTaxi s = servicio.solicitar(req.hotelId(), req.hotelNombre(), req.clienteId(), req.aeropuertoDestino());
        eventos.registrar("SERVICIO_SOLICITADO", jwt.getSubject(), s.getId(),
                "Hotel " + req.hotelNombre() + " -> " + req.aeropuertoDestino());
        return ResponseEntity.created(URI.create("/api/servicios/" + s.getId()))
                .body(ServicioResponse.desde(s, true));
    }

    /** Pedidos que un taxista puede aceptar. */
    @GetMapping("/solicitados")
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL','TAXISTA')")
    public List<ServicioResponse> solicitados(Authentication auth) {
        boolean qr = !esTaxista(auth);
        return servicio.solicitados().stream().map(s -> ServicioResponse.desde(s, qr)).toList();
    }

    /** Estado del servicio para la app movil, el hotel o el taxista asignado. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','APP_MOVIL','TAXISTA')")
    public ServicioResponse obtener(@PathVariable String id, Authentication auth) {
        ServicioTaxi s = servicio.obtener(id);
        if (esTaxista(auth) && !auth.getName().equals(s.getTaxistaId())) {
            throw new AccessDeniedException("El servicio esta asignado a otro taxista");
        }
        return ServicioResponse.desde(s, !esTaxista(auth));
    }

    @PostMapping("/{id}/aceptar")
    @PreAuthorize("hasRole('TAXISTA')")
    public ServicioResponse aceptar(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        ServicioTaxi s = servicio.aceptar(id, jwt.getSubject());
        eventos.registrar("SERVICIO_ASIGNADO", jwt.getSubject(), id, "Taxista asignado");
        return ServicioResponse.desde(s, false);
    }

    /** ASIGNADO -> EN_CAMINO -> EN_TRASLADO. */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('TAXISTA')")
    public ServicioResponse avanzar(@PathVariable String id,
                                    @Valid @RequestBody AvanzarServicioRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        ServicioTaxi s = servicio.avanzar(id, jwt.getSubject(), req.estado());
        eventos.registrar("SERVICIO_" + req.estado(), jwt.getSubject(), id, "Estado actualizado");
        return ServicioResponse.desde(s, false);
    }

    /** FINALIZADO solo se registra al validar el QR que el cliente le muestra al taxista. */
    @PostMapping("/{id}/finalizar")
    @PreAuthorize("hasRole('TAXISTA')")
    public ServicioResponse finalizar(@PathVariable String id,
                                      @Valid @RequestBody FinalizarServicioRequest req,
                                      @AuthenticationPrincipal Jwt jwt) {
        ServicioTaxi s = servicio.finalizar(id, jwt.getSubject(), req.qrCodigo());
        eventos.registrar("SERVICIO_FINALIZADO", jwt.getSubject(), id, "QR validado");
        return ServicioResponse.desde(s, false);
    }

    private boolean esTaxista(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TAXISTA"));
    }
}
