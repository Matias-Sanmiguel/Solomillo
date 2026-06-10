package dev.solomillo.stats;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "estadisticas_equipo",
       uniqueConstraints = @UniqueConstraint(columnNames = {"equipo_id", "torneo_id", "metrica"}))
public class EstadisticaEquipo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long equipoId;
    private Long torneoId;
    private String metrica;
    private double valor;
}
