package dev.solomillo.repository;

import dev.solomillo.events.EventoDeportivo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventoDeportivoRepository extends JpaRepository<EventoDeportivo, Long> {
    long countByPartidoIdAndTipoAndJugadorIdIn(Long partidoId, String tipo, List<Long> jugadorIds);
    List<EventoDeportivo> findByPartidoIdAndTipo(Long partidoId, String tipo);
}
