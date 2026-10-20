package pe.pucp.tel05.taxi.taxista.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import pe.pucp.tel05.taxi.taxista.domain.EstadoServicio;
import pe.pucp.tel05.taxi.taxista.domain.ServicioTaxi;

@Repository
public interface ServicioTaxiRepository extends MongoRepository<ServicioTaxi, String> {

    List<ServicioTaxi> findByEstadoOrderByFechaSolicitudAsc(EstadoServicio estado);

    List<ServicioTaxi> findByTaxistaIdOrderByFechaSolicitudDesc(String taxistaId);
}
