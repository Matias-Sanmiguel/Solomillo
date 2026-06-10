package dev.solomillo.stats;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "estadisticas_jugador",
       uniqueConstraints = @UniqueConstraint(columnNames = {"jugador_id", "torneo_id", "metrica"}))
public class EstadisticaJugador {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jugadorId;
    private Long torneoId;
    private String metrica;
    private double valor;
}
