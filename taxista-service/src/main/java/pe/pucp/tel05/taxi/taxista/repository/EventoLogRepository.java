package pe.pucp.tel05.taxi.taxista.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import pe.pucp.tel05.taxi.taxista.domain.EventoLog;

@Repository
public interface EventoLogRepository extends MongoRepository<EventoLog, String> {

    List<EventoLog> findTop200ByOrderByFechaDesc();
}
