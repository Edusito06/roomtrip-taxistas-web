package pe.pucp.tel05.taxi.taxista.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.pucp.tel05.taxi.taxista.domain.EstadoDisponibilidad;
import pe.pucp.tel05.taxi.taxista.domain.EstadoHabilitacion;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.repository.TaxistaRepository;
import pe.pucp.tel05.taxi.taxista.web.dto.ActualizarPerfilRequest;
import pe.pucp.tel05.taxi.taxista.web.dto.RegistroTaxistaRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pruebas de las reglas que el jefe de practica va a mirar primero:
 * el estado inicial de la solicitud, el hash de la contrasenia y la
 * restriccion de que un taxista no aprobado no puede estar disponible.
 */
class TaxistaServiceTest {

    private TaxistaRepository repositorio;
    private PasswordEncoder passwordEncoder;
    private TaxistaService servicio;

    @BeforeEach
    void setUp() {
        repositorio = mock(TaxistaRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        servicio = new TaxistaService(repositorio, passwordEncoder);

        when(repositorio.existsByCorreo(anyString())).thenReturn(false);
        when(repositorio.existsByNumeroDocumento(anyString())).thenReturn(false);
        when(repositorio.existsByPlaca(anyString())).thenReturn(false);
        when(repositorio.save(any(Taxista.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("RF-WTX-003: un autoregistro queda PENDIENTE y NO_DISPONIBLE")
    void registroQuedaPendiente() {
        Taxista creado = servicio.registrar(solicitudValida());

        assertEquals(EstadoHabilitacion.PENDIENTE, creado.getEstadoHabilitacion());
        assertEquals(EstadoDisponibilidad.NO_DISPONIBLE, creado.getEstadoDisponibilidad());
    }

    @Test
    @DisplayName("Calidad: la contrasenia nunca se guarda en texto plano")
    void passwordSeGuardaHasheada() {
        Taxista creado = servicio.registrar(solicitudValida());

        assertNotEquals("secreto123", creado.getPasswordHash());
        assertTrue(passwordEncoder.matches("secreto123", creado.getPasswordHash()));
    }

    @Test
    @DisplayName("Un correo ya registrado se rechaza con error de negocio")
    void correoDuplicadoFalla() {
        when(repositorio.existsByCorreo(anyString())).thenReturn(true);

        assertThrows(TaxistaService.ReglaNegocioException.class,
                () -> servicio.registrar(solicitudValida()));
    }

    @Test
    @DisplayName("RF-WTX-003: un taxista PENDIENTE no puede declararse DISPONIBLE")
    void pendienteNoPuedeEstarDisponible() {
        Taxista pendiente = new Taxista();
        pendiente.setId("t1");
        pendiente.setEstadoHabilitacion(EstadoHabilitacion.PENDIENTE);
        when(repositorio.findById("t1")).thenReturn(Optional.of(pendiente));

        assertThrows(TaxistaService.ReglaNegocioException.class,
                () -> servicio.cambiarDisponibilidad("t1", EstadoDisponibilidad.DISPONIBLE));
    }

    @Test
    @DisplayName("RF-WTX-004: una solicitud ya decidida no se puede volver a procesar")
    void noSePuedeDecidirDosVeces() {
        Taxista aprobado = new Taxista();
        aprobado.setId("t2");
        aprobado.setEstadoHabilitacion(EstadoHabilitacion.APROBADO);
        when(repositorio.findById("t2")).thenReturn(Optional.of(aprobado));

        assertThrows(TaxistaService.ReglaNegocioException.class,
                () -> servicio.decidirSolicitud("t2", true, null, "admin"));
    }

    @Test
    @DisplayName("RF-API-001: disponibles filtra por APROBADO + DISPONIBLE")
    void disponiblesFiltraPorAmbosEstados() {
        when(repositorio.findByEstadoHabilitacionAndEstadoDisponibilidad(
                EstadoHabilitacion.APROBADO, EstadoDisponibilidad.DISPONIBLE))
                .thenReturn(List.of(new Taxista()));

        assertEquals(1, servicio.listarDisponibles().size());
    }

    @Test
    @DisplayName("RF-WTX-005: editar perfil actualiza los campos permitidos sin tocar el estado")
    void editarPerfilActualizaCamposPermitidos() {
        Taxista existente = new Taxista();
        existente.setId("t3");
        existente.setCorreo("viejo@example.com");
        existente.setPlaca("AAA-111");
        existente.setEstadoHabilitacion(EstadoHabilitacion.APROBADO);
        existente.setEstadoDisponibilidad(EstadoDisponibilidad.DISPONIBLE);
        when(repositorio.findById("t3")).thenReturn(Optional.of(existente));

        Taxista actualizado = servicio.actualizarPerfil("t3", perfilValido("nuevo@example.com", "BBB-222", null));

        assertEquals("nuevo@example.com", actualizado.getCorreo());
        assertEquals("BBB-222", actualizado.getPlaca());
        assertEquals(EstadoHabilitacion.APROBADO, actualizado.getEstadoHabilitacion());
        assertEquals(EstadoDisponibilidad.DISPONIBLE, actualizado.getEstadoDisponibilidad());
    }

    @Test
    @DisplayName("Editar perfil con el correo de otro taxista se rechaza con error de negocio")
    void editarPerfilConCorreoDeOtroFalla() {
        Taxista existente = new Taxista();
        existente.setId("t4");
        existente.setCorreo("propio@example.com");
        existente.setPlaca("AAA-111");
        when(repositorio.findById("t4")).thenReturn(Optional.of(existente));
        when(repositorio.existsByCorreo("ocupado@example.com")).thenReturn(true);

        assertThrows(TaxistaService.ReglaNegocioException.class,
                () -> servicio.actualizarPerfil("t4", perfilValido("ocupado@example.com", "AAA-111", null)));
    }

    @Test
    @DisplayName("Editar perfil sin tocar el correo propio no dispara el chequeo de duplicado")
    void editarPerfilConElMismoCorreoNoFalla() {
        Taxista existente = new Taxista();
        existente.setId("t5");
        existente.setCorreo("propio@example.com");
        existente.setPlaca("AAA-111");
        when(repositorio.findById("t5")).thenReturn(Optional.of(existente));
        when(repositorio.existsByCorreo(anyString())).thenReturn(true);

        Taxista actualizado = servicio.actualizarPerfil(
                "t5", perfilValido("propio@example.com", "AAA-111", null));

        assertEquals("propio@example.com", actualizado.getCorreo());
    }

    @Test
    @DisplayName("Editar perfil con password en blanco no cambia el hash existente")
    void editarPerfilConPasswordEnBlancoNoCambiaHash() {
        Taxista existente = new Taxista();
        existente.setId("t6");
        existente.setCorreo("propio@example.com");
        existente.setPlaca("AAA-111");
        existente.setPasswordHash("hash-original");
        when(repositorio.findById("t6")).thenReturn(Optional.of(existente));

        Taxista actualizado = servicio.actualizarPerfil("t6", perfilValido("propio@example.com", "AAA-111", ""));

        assertEquals("hash-original", actualizado.getPasswordHash());
    }

    @Test
    @DisplayName("Editar perfil con password nueva pero muy corta se rechaza")
    void editarPerfilConPasswordCortaFalla() {
        Taxista existente = new Taxista();
        existente.setId("t7");
        existente.setCorreo("propio@example.com");
        existente.setPlaca("AAA-111");
        when(repositorio.findById("t7")).thenReturn(Optional.of(existente));

        assertThrows(TaxistaService.ReglaNegocioException.class,
                () -> servicio.actualizarPerfil("t7", perfilValido("propio@example.com", "AAA-111", "123")));
    }

    @Test
    @DisplayName("El registro guarda fecha de nacimiento y domicilio (datos exigidos por el plan de proyecto)")
    void registroGuardaFechaNacimientoYDomicilio() {
        Taxista creado = servicio.registrar(solicitudValida());

        assertEquals(java.time.LocalDate.of(1990, 5, 20), creado.getFechaNacimiento());
        assertEquals("Av. Larco 123, Miraflores", creado.getDomicilio());
    }

    private ActualizarPerfilRequest perfilValido(String correo, String placa, String password) {
        return new ActualizarPerfilRequest(
                "Juan", "Perez", "987654321", "Av. Larco 123, Miraflores", correo, password,
                "Toyota", "Yaris", "2020", "Blanco", placa,
                "https://res.cloudinary.com/demo/foto.jpg",
                "https://res.cloudinary.com/demo/vehiculo.jpg");
    }

    private RegistroTaxistaRequest solicitudValida() {
        return new RegistroTaxistaRequest(
                "Juan", "Perez", "DNI", "70123456", java.time.LocalDate.of(1990, 5, 20),
                "juan.perez@example.com", "987654321", "Av. Larco 123, Miraflores", "secreto123",
                "https://res.cloudinary.com/demo/foto.jpg",
                "Toyota", "Yaris", "2020", "Blanco", "ABC-123",
                "https://res.cloudinary.com/demo/vehiculo.jpg");
    }
}
