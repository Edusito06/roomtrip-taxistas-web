package pe.pucp.tel05.taxi.taxista.service;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.pucp.tel05.taxi.taxista.service.AuthService.DemasiadosIntentosException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5 fallos en 15 minutos bloquean el correo 15 minutos; un exito reinicia la cuenta. */
class LimiteIntentosLoginTest {

    private RelojManual reloj;
    private LimiteIntentosLogin limite;

    @BeforeEach
    void setUp() {
        reloj = new RelojManual();
        limite = new LimiteIntentosLogin(5, Duration.ofMinutes(15), reloj);
    }

    private void fallar(String clave, int veces) {
        for (int i = 0; i < veces; i++) {
            limite.registrarFallo(clave);
        }
    }

    @Test
    @DisplayName("Con menos fallos que el limite se sigue pudiendo intentar")
    void bajoElLimiteNoBloquea() {
        fallar("a@x.pe", 4);

        assertDoesNotThrow(() -> limite.verificar("a@x.pe"));
    }

    @Test
    @DisplayName("El quinto fallo bloquea y avisa que es el que dispara el bloqueo")
    void elQuintoFalloBloquea() {
        fallar("a@x.pe", 4);

        DemasiadosIntentosException e = assertThrows(DemasiadosIntentosException.class,
                () -> limite.registrarFallo("a@x.pe"));
        assertTrue(e.esNuevo());
        assertEquals(15 * 60, e.getSegundos());
    }

    @Test
    @DisplayName("Bloqueado: se rechaza aunque la contrasenia fuera correcta, y ya no es 'nuevo'")
    void bloqueadoRechazaLosSiguientes() {
        fallar("a@x.pe", 4);
        assertThrows(DemasiadosIntentosException.class, () -> limite.registrarFallo("a@x.pe"));

        reloj.avanzar(Duration.ofMinutes(5));
        DemasiadosIntentosException e = assertThrows(DemasiadosIntentosException.class,
                () -> limite.verificar("a@x.pe"));

        assertFalse(e.esNuevo());
        assertTrue(e.getSegundos() <= 10 * 60 + 1 && e.getSegundos() > 9 * 60, "quedan ~10 minutos");
    }

    @Test
    @DisplayName("Pasado el bloqueo se puede volver a intentar, y el contador empieza de cero")
    void elBloqueoExpira() {
        fallar("a@x.pe", 4);
        assertThrows(DemasiadosIntentosException.class, () -> limite.registrarFallo("a@x.pe"));

        reloj.avanzar(Duration.ofMinutes(16));

        assertDoesNotThrow(() -> limite.verificar("a@x.pe"));
        fallar("a@x.pe", 4);
        assertDoesNotThrow(() -> limite.verificar("a@x.pe"));
    }

    @Test
    @DisplayName("Un login correcto reinicia los fallos acumulados")
    void exitoReiniciaElContador() {
        fallar("a@x.pe", 4);
        limite.registrarExito("a@x.pe");

        fallar("a@x.pe", 4);
        assertDoesNotThrow(() -> limite.verificar("a@x.pe"));
    }

    @Test
    @DisplayName("Fallos espaciados mas que la ventana no se acumulan")
    void losFallosViejosNoCuentan() {
        fallar("a@x.pe", 4);
        reloj.avanzar(Duration.ofMinutes(20));

        assertDoesNotThrow(() -> limite.registrarFallo("a@x.pe"));
    }

    @Test
    @DisplayName("Cada correo tiene su propio contador")
    void correosIndependientes() {
        fallar("a@x.pe", 4);
        assertThrows(DemasiadosIntentosException.class, () -> limite.registrarFallo("a@x.pe"));

        assertDoesNotThrow(() -> limite.verificar("b@x.pe"));
        assertDoesNotThrow(() -> limite.registrarFallo("b@x.pe"));
    }
}
