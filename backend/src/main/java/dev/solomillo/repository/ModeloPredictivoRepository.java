package dev.solomillo.repository;

import dev.solomillo.ml.ModeloPredictivo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ModeloPredictivoRepository extends JpaRepository<ModeloPredictivo, Long> {
    Optional<ModeloPredictivo> findByNombreAndActivoTrue(String nombre);
    Optional<ModeloPredictivo> findFirstByNombreOrderByVersionDesc(String nombre);
    List<ModeloPredictivo> findByNombre(String nombre);
}
