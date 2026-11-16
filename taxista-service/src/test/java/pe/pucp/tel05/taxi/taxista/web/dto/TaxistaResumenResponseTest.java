package pe.pucp.tel05.taxi.taxista.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.pucp.tel05.taxi.taxista.domain.Taxista;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** La vista que recibe la app no debe llevar datos personales que no necesita. */
class TaxistaResumenResponseTest {

    @Test
    @DisplayName("El JSON que sale hacia la app no incluye documento, nacimiento, domicilio ni contrasenia")
    void noFiltraDatosPersonales() throws Exception {
        Taxista t = new Taxista();
        t.setId("t1");
        t.setNombres("Juan");
        t.setApellidos("Perez");
        t.setCorreo("juan@x.pe");
        t.setNumeroDocumento("45678912");
        t.setDomicilio("Av. Larco 123");
        t.setFechaNacimiento(java.time.LocalDate.of(1990, 5, 20));
        t.setPasswordHash("$2a$10$secreto");
        t.setPlaca("ABC-123");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(TaxistaResumenResponse.desde(t)));

        for (String prohibido : new String[] {"numeroDocumento", "tipoDocumento", "fechaNacimiento",
                "domicilio", "passwordHash", "motivoRechazo", "motivoInhabilitacion"}) {
            assertFalse(json.has(prohibido), "no debe exponer " + prohibido);
        }
        assertFalse(json.toString().contains("45678912"));
        assertFalse(json.toString().contains("secreto"));
    }

    @Test
    @DisplayName("Si incluye lo que la app necesita: id, nombre, vehiculo, placa, estados y valoracion")
    void incluyeLoNecesario() {
        Taxista t = new Taxista();
        t.setId("t1");
        t.setNombres("Juan");
        t.setPlaca("ABC-123");
        t.setValoracionPromedio(4.5);

        TaxistaResumenResponse r = TaxistaResumenResponse.desde(t);

        assertEquals("t1", r.id());
        assertEquals("ABC-123", r.placa());
        assertEquals("PENDIENTE", r.estadoHabilitacion());
        assertEquals("NO_DISPONIBLE", r.estadoDisponibilidad());
        assertEquals(4.5, r.valoracionPromedio());
    }
}
