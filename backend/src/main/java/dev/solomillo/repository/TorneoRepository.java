package dev.solomillo.repository;

import dev.solomillo.domain.Torneo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TorneoRepository extends JpaRepository<Torneo, Long> {
    Optional<Torneo> findByNombreAndTemporada(String nombre, String temporada);
}
