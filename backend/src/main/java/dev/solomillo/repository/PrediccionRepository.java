package dev.solomillo.repository;

import dev.solomillo.ml.Prediccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrediccionRepository extends JpaRepository<Prediccion, Long> {
    List<Prediccion> findByModeloVersion(int modeloVersion);
    List<Prediccion> findByResultadoRealIsNotNull();
    Optional<Prediccion> findFirstByPartidoIdOrderByPredichoEnDesc(Long partidoId);
    List<Prediccion> findByPartidoIdAndResultadoRealIsNull(Long partidoId);
    Optional<Prediccion> findByPartidoIdAndModeloVersion(Long partidoId, int modeloVersion);
}
