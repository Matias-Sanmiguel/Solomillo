package dev.solomillo.repository;

import dev.solomillo.stats.EstadisticaEquipo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EstadisticaEquipoRepository extends JpaRepository<EstadisticaEquipo, Long> {
    Optional<EstadisticaEquipo> findByEquipoIdAndTorneoIdAndMetrica(Long equipoId, Long torneoId, String metrica);
    List<EstadisticaEquipo> findByEquipoId(Long equipoId);
}
