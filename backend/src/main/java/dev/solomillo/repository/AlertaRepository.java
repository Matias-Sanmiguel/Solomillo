package dev.solomillo.repository;

import dev.solomillo.alerts.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {
    List<Alerta> findByPartidoIdOrderByCreadoEnDesc(Long partidoId);
}
