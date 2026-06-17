package dev.solomillo.repository;

import dev.solomillo.ml.EloHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EloHistorialRepository extends JpaRepository<EloHistorial, Long> {
    List<EloHistorial> findByEquipoIdOrderByFechaAsc(Long equipoId);
}
