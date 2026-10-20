package pe.pucp.tel05.taxi.taxista.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pe.pucp.tel05.taxi.taxista.domain.Administrador;
import pe.pucp.tel05.taxi.taxista.repository.AdministradorRepository;

/**
 * Crea al arrancar el Superadmin y la credencial de servicio de la app movil,
 * si todavia no existen. Sin esto no habria forma de entrar al portal la
 * primera vez.
 *
 * <p>Es idempotente y el secreto manda: si el correo ya existe, su contrasenia se
 * sincroniza con el valor configurado solo cuando cambio. Asi rotar una clave es
 * cambiar el secreto y reiniciar, sin tocar la base de datos.
 *
 * <p>Con {@code app.carga-inicial.exigir-claves-fuertes=true} (produccion) el
 * servicio se niega a arrancar con las claves por defecto o demasiado cortas.
 */
@Component
public class CargaInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    private static final String CLAVE_POR_DEFECTO = "cambiame";

    private final AdministradorRepository repositorio;
    private final PasswordEncoder passwordEncoder;
    private final String superadminCorreo;
    private final String superadminPassword;
    private final String appMovilCorreo;
    private final String appMovilPassword;
    private final String jwtSecret;
    private final boolean exigirClavesFuertes;

    public CargaInicial(AdministradorRepository repositorio,
                        PasswordEncoder passwordEncoder,
                        @Value("${app.carga-inicial.superadmin-correo}") String superadminCorreo,
                        @Value("${app.carga-inicial.superadmin-password}") String superadminPassword,
                        @Value("${app.carga-inicial.app-movil-correo}") String appMovilCorreo,
                        @Value("${app.carga-inicial.app-movil-password}") String appMovilPassword,
                        @Value("${app.jwt.secret}") String jwtSecret,
                        @Value("${app.carga-inicial.exigir-claves-fuertes:false}") boolean exigirClavesFuertes) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
        this.superadminCorreo = superadminCorreo;
        this.superadminPassword = superadminPassword;
        this.appMovilCorreo = appMovilCorreo;
        this.appMovilPassword = appMovilPassword;
        this.jwtSecret = jwtSecret;
        this.exigirClavesFuertes = exigirClavesFuertes;
    }

    @Override
    public void run(ApplicationArguments args) {
        validarClaves();
        sincronizar(superadminCorreo, superadminPassword, "SUPERADMIN");
        sincronizar(appMovilCorreo, appMovilPassword, "APP_MOVIL");
    }

    /** Clave por defecto o corta: aviso en desarrollo, error que impide arrancar en produccion. */
    private void validarClaves() {
        List<String> debiles = new ArrayList<>();
        if (esDebil(superadminPassword)) {
            debiles.add("SUPERADMIN_PASSWORD");
        }
        if (esDebil(appMovilPassword)) {
            debiles.add("APP_MOVIL_PASSWORD");
        }
        if (jwtSecret.startsWith(CLAVE_POR_DEFECTO) || jwtSecret.length() < 32) {
            debiles.add("JWT_SECRET");
        }
        if (debiles.isEmpty()) {
            return;
        }
        if (exigirClavesFuertes) {
            throw new IllegalStateException("Claves por defecto o demasiado cortas en produccion: "
                    + String.join(", ", debiles) + ". Configura valores propios de al menos 12 caracteres.");
        }
        log.warn("Usando claves por defecto o cortas ({}). Aceptable solo en desarrollo local.",
                String.join(", ", debiles));
    }

    private boolean esDebil(String password) {
        return password == null || password.startsWith(CLAVE_POR_DEFECTO) || password.length() < 12;
    }

    private void sincronizar(String correo, String password, String rol) {
        String normalizado = correo.toLowerCase().trim();
        Optional<Administrador> existente = repositorio.findByCorreo(normalizado);
        if (existente.isEmpty()) {
            repositorio.save(new Administrador(normalizado, passwordEncoder.encode(password), rol));
            log.info("Usuario inicial creado: {} con rol {}", normalizado, rol);
            return;
        }
        Administrador admin = existente.get();
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(password));
            repositorio.save(admin);
            log.info("Contrasenia de {} actualizada desde el secreto configurado", normalizado);
        }
    }
}
