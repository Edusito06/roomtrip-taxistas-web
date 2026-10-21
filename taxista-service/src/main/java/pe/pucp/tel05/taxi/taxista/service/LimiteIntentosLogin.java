package pe.pucp.tel05.taxi.taxista.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.pucp.tel05.taxi.taxista.service.AuthService.DemasiadosIntentosException;

/**
 * Freno contra adivinar contrasenias: tras {@code max-intentos} fallos seguidos para un mismo
 * correo dentro de la ventana, ese correo queda bloqueado {@code bloqueo-minutos}. Un login
 * correcto reinicia el contador.
 *
 * <p>Los contadores viven en memoria de cada instancia. En Cloud Run (hasta 3 instancias) el
 * limite efectivo puede ser hasta 3 veces mayor y se reinicia al reciclarse la instancia: frena
 * un ataque de fuerza bruta, pero no es un control exacto. Si hiciera falta, se movera a Mongo.
 *
 * <p>Se cuenta por correo, exista o no: asi no se revela si un correo esta registrado. El
 * costo es que alguien puede bloquear temporalmente la cuenta de otro; por eso el bloqueo es corto.
 */
@Component
public class LimiteIntentosLogin {

    private static final int LIMPIAR_DESDE = 10_000;

    private record Estado(int fallos, Instant primerFallo, Instant bloqueadoHasta) {
    }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();
    private final int maxIntentos;
    private final Duration bloqueo;
    private final Clock reloj;

    @Autowired
    public LimiteIntentosLogin(@Value("${app.login.max-intentos:5}") int maxIntentos,
                               @Value("${app.login.bloqueo-minutos:15}") long bloqueoMinutos) {
        this(maxIntentos, Duration.ofMinutes(bloqueoMinutos), Clock.systemUTC());
    }

    LimiteIntentosLogin(int maxIntentos, Duration bloqueo, Clock reloj) {
        this.maxIntentos = maxIntentos;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
    }

    /** Antes de mirar la contrasenia: si el correo esta bloqueado, ni se intenta. */
    public void verificar(String clave) {
        Estado e = estados.get(clave);
        if (e == null || e.bloqueadoHasta() == null) {
            return;
        }
        Instant ahora = reloj.instant();
        if (ahora.isBefore(e.bloqueadoHasta())) {
            throw new DemasiadosIntentosException(
                    Duration.between(ahora, e.bloqueadoHasta()).toSeconds() + 1, false);
        }
        estados.remove(clave, e);
    }

    /** Suma un fallo. Si con este se alcanza el limite, lanza el bloqueo (con nuevo=true). */
    public void registrarFallo(String clave) {
        Instant ahora = reloj.instant();
        Estado nuevo = estados.compute(clave, (k, e) -> {
            boolean vencido = e == null
                    || (e.bloqueadoHasta() != null && !ahora.isBefore(e.bloqueadoHasta()))
                    || ahora.isAfter(e.primerFallo().plus(bloqueo));
            int fallos = vencido ? 1 : e.fallos() + 1;
            Instant primero = vencido ? ahora : e.primerFallo();
            return new Estado(fallos, primero, fallos >= maxIntentos ? ahora.plus(bloqueo) : null);
        });
        if (estados.size() > LIMPIAR_DESDE) {
            limpiarVencidos(ahora);
        }
        if (nuevo.bloqueadoHasta() != null) {
            throw new DemasiadosIntentosException(bloqueo.toSeconds(), true);
        }
    }

    public void registrarExito(String clave) {
        estados.remove(clave);
    }

    private void limpiarVencidos(Instant ahora) {
        estados.entrySet().removeIf(en -> {
            Estado e = en.getValue();
            return e.bloqueadoHasta() != null
                    ? !ahora.isBefore(e.bloqueadoHasta())
                    : ahora.isAfter(e.primerFallo().plus(bloqueo));
        });
    }
}
