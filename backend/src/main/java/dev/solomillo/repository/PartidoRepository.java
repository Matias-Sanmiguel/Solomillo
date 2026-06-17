package dev.solomillo.repository;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartidoRepository extends JpaRepository<Partido, Long> {
    List<Partido> findByEstadoOrderByFechaHoraAsc(EstadoPartido estado);
    List<Partido> findByEstadoNotOrderByFechaHoraAsc(EstadoPartido estado);
}
