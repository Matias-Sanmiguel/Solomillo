package dev.solomillo.alerts;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "alertas")
public class Alerta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long partidoId;
    private String tipo;
    private String mensaje;
    @CreationTimestamp
    private LocalDateTime creadoEn;
}
