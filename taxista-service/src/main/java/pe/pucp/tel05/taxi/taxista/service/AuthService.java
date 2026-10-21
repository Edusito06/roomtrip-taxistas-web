package pe.pucp.tel05.taxi.taxista.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import pe.pucp.tel05.taxi.taxista.domain.Administrador;
import pe.pucp.tel05.taxi.taxista.domain.EstadoHabilitacion;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.repository.AdministradorRepository;
import pe.pucp.tel05.taxi.taxista.repository.TaxistaRepository;
import pe.pucp.tel05.taxi.taxista.security.JwtService;
import pe.pucp.tel05.taxi.taxista.web.dto.TokenResponse;

/**
 * RF-WTX-009: autenticacion de administradores y taxistas.
 *
 * <p>Se buscan primero los administradores y luego los taxistas. Un taxista
 * que no esta APROBADO puede iniciar sesion (para ver el estado de su
 * solicitud) pero su token no le sirve para operar: las rutas de operacion
 * exigen estado APROBADO.
 */
@Service
public class AuthService {

    private final AdministradorRepository administradores;
    private final TaxistaRepository taxistas;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LimiteIntentosLogin limite;

    public AuthService(AdministradorRepository administradores,
                       TaxistaRepository taxistas,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LimiteIntentosLogin limite) {
        this.administradores = administradores;
        this.taxistas = taxistas;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limite = limite;
    }

    public TokenResponse login(String correo, String password) {
        String normalizado = correo == null ? "" : correo.toLowerCase().trim();
        limite.verificar(normalizado);

        Optional<Administrador> admin = administradores.findByCorreo(normalizado);
        if (admin.isPresent() && passwordEncoder.matches(password, admin.get().getPasswordHash())) {
            Administrador a = admin.get();
            limite.registrarExito(normalizado);
            String token = jwtService.generar(a.getId(), a.getCorreo(), a.getRol());
            return TokenResponse.bearer(token, a.getRol(), a.getId(), jwtService.expiracionSegundos());
        }

        Optional<Taxista> taxista = taxistas.findByCorreo(normalizado);
        if (taxista.isPresent() && passwordEncoder.matches(password, taxista.get().getPasswordHash())) {
            Taxista t = taxista.get();
            limite.registrarExito(normalizado);
            if (t.getEstadoHabilitacion() == EstadoHabilitacion.RECHAZADO) {
                throw new CredencialesInvalidasException("Su solicitud de registro fue rechazada");
            }
            String token = jwtService.generar(t.getId(), t.getCorreo(), "TAXISTA");
            return TokenResponse.bearer(token, "TAXISTA", t.getId(), jwtService.expiracionSegundos());
        }

        // Mismo mensaje para usuario inexistente y contrasenia incorrecta:
        // no se filtra si un correo esta registrado o no. El fallo se cuenta para el limite de intentos
        // (el que alcanza el maximo ya responde 429).
        limite.registrarFallo(normalizado);
        throw new CredencialesInvalidasException("Correo o contrasenia incorrectos");
    }

    /**
     * Demasiados intentos fallidos para un correo: se traduce a HTTP 429 con Retry-After.
     * {@code nuevo} es true solo en el intento que dispara el bloqueo (para registrarlo una vez).
     */
    public static class DemasiadosIntentosException extends RuntimeException {
        private final long segundos;
        private final boolean nuevo;

        public DemasiadosIntentosException(long segundos, boolean nuevo) {
            super("Demasiados intentos fallidos. Intenta de nuevo en " + Math.max(1, (segundos + 59) / 60) + " minuto(s).");
            this.segundos = segundos;
            this.nuevo = nuevo;
        }

        public long getSegundos() {
            return segundos;
        }

        public boolean esNuevo() {
            return nuevo;
        }
    }

    /** Se traduce a HTTP 401. */
    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException(String mensaje) {
            super(mensaje);
        }
    }
}
