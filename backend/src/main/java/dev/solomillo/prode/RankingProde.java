package dev.solomillo.prode;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Proyección/snapshot del acumulado de un usuario en el prode.
 * Fuente de verdad del ranking; Redis (ZSET) actúa como índice rápido para
 * la posición individual.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "ranking_prode",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "torneo_id"}))
public class RankingProde {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Ámbito del ranking. 0 = global; cualquier otro valor = id de torneo. */
    @Column(name = "torneo_id")
    private Long torneoId;

    private String nombre;

    private int puntos;
    private int aciertos;
    private int pronosticos;
}
