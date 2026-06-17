package dev.solomillo.ml;

import dev.solomillo.domain.Equipo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "elo_historial")
public class EloHistorial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id")
    private Equipo equipo;

    private double elo;
    private LocalDateTime fecha;

    public EloHistorial(Equipo equipo, double elo, LocalDateTime fecha) {
        this.equipo = equipo;
        this.elo = elo;
        this.fecha = fecha;
    }
}
