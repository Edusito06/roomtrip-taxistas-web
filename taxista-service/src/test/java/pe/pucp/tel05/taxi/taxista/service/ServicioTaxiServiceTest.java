package pe.pucp.tel05.taxi.taxista.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import pe.pucp.tel05.taxi.taxista.domain.EstadoDisponibilidad;
import pe.pucp.tel05.taxi.taxista.domain.EstadoHabilitacion;
import pe.pucp.tel05.taxi.taxista.domain.EstadoServicio;
import pe.pucp.tel05.taxi.taxista.domain.ServicioTaxi;
import pe.pucp.tel05.taxi.taxista.domain.Taxista;
import pe.pucp.tel05.taxi.taxista.repository.ServicioTaxiRepository;
import pe.pucp.tel05.taxi.taxista.repository.TaxistaRepository;
import pe.pucp.tel05.taxi.taxista.service.TaxistaService.ReglaNegocioException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reglas del servicio de taxi: asignacion exclusiva, flujo lineal y cierre con QR. */
class ServicioTaxiServiceTest {

    private ServicioTaxiRepository servicios;
    private TaxistaRepository taxistas;
    private ServicioTaxiService servicio;
    private Taxista taxista;

    @BeforeEach
    void setUp() {
        servicios = mock(ServicioTaxiRepository.class);
        taxistas = mock(TaxistaRepository.class);
        servicio = new ServicioTaxiService(servicios, taxistas);

        taxista = new Taxista();
        taxista.setId("t1");
        taxista.setEstadoHabilitacion(EstadoHabilitacion.APROBADO);
        taxista.setEstadoDisponibilidad(EstadoDisponibilidad.DISPONIBLE);
        when(taxistas.findById("t1")).thenReturn(Optional.of(taxista));
        when(taxistas.save(any(Taxista.class))).thenAnswer(inv -> inv.getArgument(0));
        when(servicios.save(any(ServicioTaxi.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ServicioTaxi pedido(EstadoServicio estado) {
        ServicioTaxi s = new ServicioTaxi();
        s.setId("s1");
        s.setEstado(estado);
        s.setQrCodigo("QR-OK");
        if (estado != EstadoServicio.SOLICITADO) {
            s.setTaxistaId("t1");
        }
        when(servicios.findById("s1")).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    @DisplayName("Un pedido nuevo nace SOLICITADO y con un QR propio")
    void solicitarCreaSolicitadoConQr() {
        ServicioTaxi s = servicio.solicitar("h1", "Hotel Lima", "c1", "Aeropuerto Jorge Chavez");

        assertEquals(EstadoServicio.SOLICITADO, s.getEstado());
        assertNotNull(s.getQrCodigo());
    }

    @Test
    @DisplayName("Al aceptar, el servicio pasa a ASIGNADO y el taxista a EN_SERVICIO")
    void aceptarAsigna() {
        pedido(EstadoServicio.SOLICITADO);

        ServicioTaxi s = servicio.aceptar("s1", "t1");

        assertEquals(EstadoServicio.ASIGNADO, s.getEstado());
        assertEquals("t1", s.getTaxistaId());
        assertEquals(EstadoDisponibilidad.EN_SERVICIO, taxista.getEstadoDisponibilidad());
    }

    @Test
    @DisplayName("Un pedido ya asignado no se puede volver a aceptar")
    void noSeAceptaDosVeces() {
        pedido(EstadoServicio.ASIGNADO);

        assertThrows(ReglaNegocioException.class, () -> servicio.aceptar("s1", "t1"));
    }

    @Test
    @DisplayName("Si otro taxista lo tomo primero (bloqueo optimista), se responde regla de negocio")
    void conflictoDeConcurrencia() {
        pedido(EstadoServicio.SOLICITADO);
        when(servicios.save(any(ServicioTaxi.class))).thenThrow(new OptimisticLockingFailureException("v"));

        assertThrows(ReglaNegocioException.class, () -> servicio.aceptar("s1", "t1"));
    }

    @Test
    @DisplayName("Un taxista NO disponible no puede aceptar pedidos")
    void noDisponibleNoAcepta() {
        pedido(EstadoServicio.SOLICITADO);
        taxista.setEstadoDisponibilidad(EstadoDisponibilidad.NO_DISPONIBLE);

        assertThrows(ReglaNegocioException.class, () -> servicio.aceptar("s1", "t1"));
    }

    @Test
    @DisplayName("Un taxista INHABILITADO no puede aceptar pedidos")
    void inhabilitadoNoAcepta() {
        pedido(EstadoServicio.SOLICITADO);
        taxista.setEstadoHabilitacion(EstadoHabilitacion.INHABILITADO);

        assertThrows(ReglaNegocioException.class, () -> servicio.aceptar("s1", "t1"));
    }

    @Test
    @DisplayName("El flujo es lineal: ASIGNADO -> EN_CAMINO -> EN_TRASLADO, sin saltos")
    void flujoLineal() {
        pedido(EstadoServicio.ASIGNADO);
        assertThrows(ReglaNegocioException.class,
                () -> servicio.avanzar("s1", "t1", EstadoServicio.EN_TRASLADO));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.avanzar("s1", "t1", EstadoServicio.FINALIZADO));

        assertEquals(EstadoServicio.EN_CAMINO,
                servicio.avanzar("s1", "t1", EstadoServicio.EN_CAMINO).getEstado());
    }

    @Test
    @DisplayName("Solo el taxista asignado puede avanzar el servicio")
    void otroTaxistaNoAvanza() {
        pedido(EstadoServicio.ASIGNADO);

        assertThrows(AccessDeniedException.class,
                () -> servicio.avanzar("s1", "otro", EstadoServicio.EN_CAMINO));
    }

    @Test
    @DisplayName("FINALIZADO exige el QR correcto y libera al taxista")
    void finalizarConQr() {
        pedido(EstadoServicio.EN_TRASLADO);
        taxista.setEstadoDisponibilidad(EstadoDisponibilidad.EN_SERVICIO);

        assertThrows(ReglaNegocioException.class, () -> servicio.finalizar("s1", "t1", "QR-MAL"));

        ServicioTaxi s = servicio.finalizar("s1", "t1", "QR-OK");
        assertEquals(EstadoServicio.FINALIZADO, s.getEstado());
        assertNotNull(s.getFechaFinalizacion());
        assertEquals(EstadoDisponibilidad.DISPONIBLE, taxista.getEstadoDisponibilidad());
    }

    @Test
    @DisplayName("No se puede finalizar un servicio que aun no esta EN_TRASLADO")
    void noFinalizaAntesDeTiempo() {
        pedido(EstadoServicio.EN_CAMINO);

        assertThrows(ReglaNegocioException.class, () -> servicio.finalizar("s1", "t1", "QR-OK"));
    }

    @Test
    @DisplayName("La ubicacion solo se acepta con el taxista EN_SERVICIO")
    void ubicacionSoloEnServicio() {
        TaxistaRepository repo = mock(TaxistaRepository.class);
        when(repo.findById("t1")).thenReturn(Optional.of(taxista));
        when(repo.save(any(Taxista.class))).thenAnswer(inv -> inv.getArgument(0));
        TaxistaService ts = new TaxistaService(repo, null, servicios);

        assertThrows(ReglaNegocioException.class, () -> ts.actualizarUbicacion("t1", -12.0, -77.0));

        taxista.setEstadoDisponibilidad(EstadoDisponibilidad.EN_SERVICIO);
        Taxista t = ts.actualizarUbicacion("t1", -12.0, -77.0);
        assertEquals(-12.0, t.getUbicacionLatitud());
        assertNotNull(t.getUbicacionFecha());
    }
}
