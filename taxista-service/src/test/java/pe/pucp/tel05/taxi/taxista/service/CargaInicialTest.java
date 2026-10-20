package pe.pucp.tel05.taxi.taxista.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.pucp.tel05.taxi.taxista.domain.Administrador;
import pe.pucp.tel05.taxi.taxista.repository.AdministradorRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cuentas iniciales: creacion, rotacion de clave desde el secreto y rechazo de claves debiles. */
class CargaInicialTest {

    private static final String FUERTE = "Clave-Muy-Fuerte-123";
    private static final String JWT_FUERTE = "un-secreto-jwt-largo-de-al-menos-32-caracteres";

    private AdministradorRepository repo;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        repo = mock(AdministradorRepository.class);
        encoder = new BCryptPasswordEncoder();
        when(repo.findByCorreo(any())).thenReturn(Optional.empty());
    }

    private CargaInicial carga(String adminPass, String appPass, String jwt, boolean exigir) {
        return new CargaInicial(repo, encoder, "admin@x.pe", adminPass, "app@x.pe", appPass, jwt, exigir);
    }

    @Test
    @DisplayName("Crea las dos cuentas con la contrasenia hasheada, nunca en claro")
    void creaCuentasConHash() {
        carga(FUERTE, FUERTE, JWT_FUERTE, true).run(null);

        verify(repo, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.argThat(
                (Administrador a) -> a.getPasswordHash().startsWith("$2") && !a.getPasswordHash().contains(FUERTE)));
    }

    @Test
    @DisplayName("Si la clave del secreto cambio, se actualiza el hash existente")
    void rotaLaClaveDesdeElSecreto() {
        Administrador existente = new Administrador("app@x.pe", encoder.encode("clave-anterior-123"), "APP_MOVIL");
        when(repo.findByCorreo("app@x.pe")).thenReturn(Optional.of(existente));

        carga(FUERTE, FUERTE, JWT_FUERTE, true).run(null);

        assertTrue(encoder.matches(FUERTE, existente.getPasswordHash()));
    }

    @Test
    @DisplayName("Si la clave no cambio, no se reescribe la cuenta")
    void noReescribeSiNoCambio() {
        Administrador existente = new Administrador("app@x.pe", encoder.encode(FUERTE), "APP_MOVIL");
        Administrador admin = new Administrador("admin@x.pe", encoder.encode(FUERTE), "SUPERADMIN");
        when(repo.findByCorreo("app@x.pe")).thenReturn(Optional.of(existente));
        when(repo.findByCorreo("admin@x.pe")).thenReturn(Optional.of(admin));

        carga(FUERTE, FUERTE, JWT_FUERTE, true).run(null);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("En produccion no arranca con la clave por defecto 'cambiame'")
    void rechazaClavePorDefectoEnProduccion() {
        assertThrows(IllegalStateException.class,
                () -> carga(FUERTE, "cambiame", JWT_FUERTE, true).run(null));
    }

    @Test
    @DisplayName("En produccion no arranca con una clave corta ni con el JWT por defecto")
    void rechazaClaveCortaYJwtPorDefecto() {
        assertThrows(IllegalStateException.class,
                () -> carga("corta", FUERTE, JWT_FUERTE, true).run(null));
        assertThrows(IllegalStateException.class,
                () -> carga(FUERTE, FUERTE, "cambiame-por-un-secreto-largo-de-al-menos-32-caracteres", true).run(null));
    }

    @Test
    @DisplayName("En desarrollo las claves por defecto solo generan un aviso")
    void enDesarrolloSoloAvisa() {
        assertDoesNotThrow(() -> carga("cambiame", "cambiame", "cambiame", false).run(null));
    }
}
