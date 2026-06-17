package dev.solomillo.repository;

import dev.solomillo.prode.RankingProde;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RankingProdeRepository extends JpaRepository<RankingProde, Long> {
    Optional<RankingProde> findByUsuarioIdAndTorneoId(Long usuarioId, Long torneoId);
    List<RankingProde> findByTorneoIdOrderByPuntosDescAciertosDesc(Long torneoId);
    long countByTorneoIdAndPuntosGreaterThan(Long torneoId, int puntos);
}
