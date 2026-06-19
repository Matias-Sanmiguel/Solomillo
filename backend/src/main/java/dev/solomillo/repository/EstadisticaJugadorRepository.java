package dev.solomillo.repository;

import dev.solomillo.stats.EstadisticaJugador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EstadisticaJugadorRepository extends JpaRepository<EstadisticaJugador, Long> {
    Optional<EstadisticaJugador> findByJugadorIdAndTorneoIdAndMetrica(Long jugadorId, Long torneoId, String metrica);
    List<EstadisticaJugador> findByJugadorId(Long jugadorId);
    List<EstadisticaJugador> findByTorneoIdAndMetricaOrderByValorDesc(Long torneoId, String metrica);
    Optional<EstadisticaJugador> findByJugadorIdAndMetrica(Long jugadorId, String metrica);
}
