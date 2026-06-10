package dev.solomillo.repository;

import dev.solomillo.domain.Partido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartidoRepository extends JpaRepository<Partido, Long> {}
