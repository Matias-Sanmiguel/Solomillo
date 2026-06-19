package dev.solomillo.ml;

import dev.solomillo.domain.Partido;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(
    name = "predicciones",
    uniqueConstraints = @UniqueConstraint(
        columnNames = { "partido_id", "modelo_version" }
    )
)
public class Prediccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
