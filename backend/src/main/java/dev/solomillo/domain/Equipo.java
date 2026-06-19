package dev.solomillo.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "equipos")
public class Equipo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String escudo;
    private String entrenador;
    private String sede;
    private String estadio;

    // Grupo del Mundial (A-L). Null para equipos sin grupo asignado.
    private String grupo;

    private Integer puntosFifa;
    private Double elo = 1500.0;
}
