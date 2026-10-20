package pe.pucp.tel05.taxi.taxista.domain;

/**
 * Estados del servicio de taxi hotel -> aeropuerto (plan de proyecto).
 * El flujo es lineal: SOLICITADO -> ASIGNADO -> EN_CAMINO -> EN_TRASLADO -> FINALIZADO.
 */
public enum EstadoServicio {
    SOLICITADO,
    ASIGNADO,
    EN_CAMINO,
    EN_TRASLADO,
    FINALIZADO
}
