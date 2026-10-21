package pe.pucp.tel05.taxi.taxista.service;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.pucp.tel05.taxi.taxista.domain.Administrador;
import pe.pucp.tel05.taxi.taxista.domain.EstadoHabilitacion;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.repository.AdministradorRepository;
import pe.pucp.tel05.taxi.taxista.repository.TaxistaRepository;
import pe.pucp.tel05.taxi.taxista.security.JwtService;
import pe.pucp.tel05.taxi.taxista.service.AuthService.CredencialesInvalidasException;
import pe.pucp.tel05.taxi.taxista.service.AuthService.DemasiadosIntentosException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El login frena la fuerza bruta sin romper el acceso legitimo. */
class AuthServiceTest {

    private static final String CLAVE = "Clave-Correcta-1";

    private AdministradorRepository admins;
    private TaxistaRepository taxistas;
    private PasswordEncoder encoder;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        admins = mock(AdministradorRepository.class);
        taxistas = mock(TaxistaRepository.class);
        encoder = new BCryptPasswordEncoder();
        JwtService jwt = mock(JwtService.class);
        when(jwt.generar(anyString(), anyString(), anyString())).thenReturn("token");
        when(admins.findByCorreo(anyString())).thenReturn(Optional.empty());
        when(taxistas.findByCorreo(anyString())).thenReturn(Optional.empty());

        Administrador admin = new Administrador("admin@x.pe", encoder.encode(CLAVE), "SUPERADMIN");
        admin.setId("a1");
        when(admins.findByCorreo("admin@x.pe")).thenReturn(Optional.of(admin));

        auth = new AuthService(admins, taxistas, encoder, jwt,
                new LimiteIntentosLogin(5, Duration.ofMinutes(15), new RelojManual()));
    }

    private void fallar(String correo, int veces) {
        for (int i = 0; i < veces; i++) {
            assertThrows(CredencialesInvalidasException.class, () -> auth.login(correo, "mala"));
        }
    }

    @Test
    @DisplayName("Credenciales correctas entran normalmente")
    void loginCorrecto() {
        assertEquals("token", auth.login("admin@x.pe", CLAVE).token());
    }

    @Test
    @DisplayName("Cuatro fallos son 401; el quinto ya es 429 (bloqueado)")
    void elQuintoFalloBloquea() {
        fallar("admin@x.pe", 4);

        assertThrows(DemasiadosIntentosException.class, () -> auth.login("admin@x.pe", "mala"));
    }

    @Test
    @DisplayName("Bloqueado, ni la contrasenia correcta entra")
    void bloqueadoNoDejaEntrar() {
        fallar("admin@x.pe", 4);
        assertThrows(DemasiadosIntentosException.class, () -> auth.login("admin@x.pe", "mala"));

        assertThrows(DemasiadosIntentosException.class, () -> auth.login("admin@x.pe", CLAVE));
    }

    @Test
    @DisplayName("Un login correcto antes del limite reinicia los fallos")
    void exitoReiniciaFallos() {
        fallar("admin@x.pe", 4);
        auth.login("admin@x.pe", CLAVE);

        fallar("admin@x.pe", 4);
    }

    @Test
    @DisplayName("Un correo que no existe tambien se cuenta, para no revelar quien esta registrado")
    void correoInexistenteTambienSeCuenta() {
        fallar("nadie@x.pe", 4);

        assertThrows(DemasiadosIntentosException.class, () -> auth.login("nadie@x.pe", "mala"));
    }

    @Test
    @DisplayName("Bloquear un correo no afecta a los demas")
    void otrosCorreosSiguenEntrando() {
        fallar("admin@x.pe", 4);
        assertThrows(DemasiadosIntentosException.class, () -> auth.login("admin@x.pe", "mala"));

        Taxista t = new Taxista();
        t.setId("t1");
        t.setCorreo("t@x.pe");
        t.setPasswordHash(encoder.encode(CLAVE));
        t.setEstadoHabilitacion(EstadoHabilitacion.APROBADO);
        when(taxistas.findByCorreo("t@x.pe")).thenReturn(Optional.of(t));

        assertEquals("TAXISTA", auth.login("t@x.pe", CLAVE).rol());
    }

    @Test
    @DisplayName("Un taxista rechazado con la clave correcta recibe su aviso y no suma fallos")
    void rechazadoNoCuentaComoFallo() {
        Taxista t = new Taxista();
        t.setId("t2");
        t.setCorreo("r@x.pe");
        t.setPasswordHash(encoder.encode(CLAVE));
        t.setEstadoHabilitacion(EstadoHabilitacion.RECHAZADO);
        when(taxistas.findByCorreo("r@x.pe")).thenReturn(Optional.of(t));

        for (int i = 0; i < 8; i++) {
            assertThrows(CredencialesInvalidasException.class, () -> auth.login("r@x.pe", CLAVE));
        }
    }
}
