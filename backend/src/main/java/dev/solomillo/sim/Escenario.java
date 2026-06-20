package dev.solomillo.sim;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Snapshot nombrado del estado simulable del torneo (partidos, posiciones, estadísticas, Elo,
 * predicciones y modelo activo) serializado como JSON. Permite crear varias simulaciones a partir
 * del mismo punto y comparar desenlaces (ej. "Argentina campeón" vs "Francia campeón").
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "escenarios")
public class Escenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    /** Estado serializado (JSON) de las tablas afectadas por la simulación. */
    @Lob
    @Column(columnDefinition = "text")
    private String snapshot;
}
