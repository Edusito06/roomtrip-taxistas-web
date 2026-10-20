package pe.pucp.tel05.taxi.taxista.web;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.pucp.tel05.taxi.taxista.service.AuthService;
import pe.pucp.tel05.taxi.taxista.service.EventoService;
import pe.pucp.tel05.taxi.taxista.web.dto.LoginRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.TokenResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EventoService eventos;

    public AuthController(AuthService authService, EventoService eventos) {
        this.authService = authService;
        this.eventos = eventos;
    }

    /** RF-WTX-009. Lo consumen el portal web y la app Android. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        try {
            TokenResponse token = authService.login(req.correo(), req.password());
            eventos.registrar("LOGIN_OK", req.correo(), token.subject(), "Rol " + token.rol());
            return ResponseEntity.ok(token);
        } catch (AuthService.CredencialesInvalidasException e) {
            eventos.registrar("LOGIN_FALLIDO", req.correo(), null, e.getMessage());
            throw e;
        }
    }
}
