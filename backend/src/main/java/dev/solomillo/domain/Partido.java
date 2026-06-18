package dev.solomillo.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "partidos")
public class Partido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_local_id")
    private Equipo equipoLocal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_visitante_id")
    private Equipo equipoVisitante;

    private LocalDateTime fechaHora;
    private String estadio;

    // cancha neutral (típico de fase final de Mundial): suprime la ventaja de local en el Elo
    private boolean neutral = false;

    private Integer golesLocal;
    private Integer golesVisitante;

    @Enumerated(EnumType.STRING)
    private EstadoPartido estado = EstadoPartido.PROGRAMADO;
}
