package pe.pucp.tel05.taxi.taxista.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que solo avanza cuando se le dice, para no dormir los tests. */
class RelojManual extends Clock {

    private Instant ahora = Instant.parse("2026-10-21T10:00:00Z");

    void avanzar(Duration d) {
        ahora = ahora.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
