package dev.solomillo.ml;

import dev.solomillo.domain.Partido;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "predicciones")
public class Prediccion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_id")
    private Partido partido;

    private int modeloVersion;

    private double probLocal;
    private double probEmpate;
    private double probVisitante;

    private Integer resultadoReal;

    @CreationTimestamp
    private LocalDateTime predichoEn;
}
