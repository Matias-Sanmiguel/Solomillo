package dev.solomillo.ml;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "modelos")
public class ModeloPredictivo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private int version;
    private String tipo;
    private String ruta;
    private double accuracy;
    private double logLoss;
    private double brier;
    private boolean activo;
    @CreationTimestamp
    private LocalDateTime creadoEn;
}
