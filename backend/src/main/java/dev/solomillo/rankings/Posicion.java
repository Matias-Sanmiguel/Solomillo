package dev.solomillo.rankings;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "posiciones",
       uniqueConstraints = @UniqueConstraint(columnNames = {"torneo_id", "equipo_id"}))
public class Posicion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long torneoId;
    private Long equipoId;
    private int puntos;
    private int ganados;
    private int empatados;
    private int perdidos;
    private int golesFavor;
    private int golesContra;
}
