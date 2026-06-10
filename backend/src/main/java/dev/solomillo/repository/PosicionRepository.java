package dev.solomillo.repository;

import dev.solomillo.rankings.Posicion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PosicionRepository extends JpaRepository<Posicion, Long> {
    Optional<Posicion> findByTorneoIdAndEquipoId(Long torneoId, Long equipoId);
    List<Posicion> findByTorneoIdOrderByPuntosDescGolesFavorDesc(Long torneoId);
}
