package dev.solomillo.repository;

import dev.solomillo.prode.Pronostico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PronosticoRepository extends JpaRepository<Pronostico, Long> {
    Optional<Pronostico> findByUsuarioIdAndPartidoId(Long usuarioId, Long partidoId);
    List<Pronostico> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    List<Pronostico> findByPartidoIdAndPuntosIsNull(Long partidoId);
}
