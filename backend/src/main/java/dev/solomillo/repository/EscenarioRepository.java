package dev.solomillo.repository;

import dev.solomillo.sim.Escenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscenarioRepository extends JpaRepository<Escenario, Long> {
    List<Escenario> findAllByOrderByCreadoEnDesc();
}
