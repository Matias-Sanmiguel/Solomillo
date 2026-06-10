package dev.solomillo.events;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "eventos")
public class EventoDeportivo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long partidoId;
    private Long jugadorId;
    private String tipo;
    private int minuto;
    private String fuente;
    @CreationTimestamp
    private LocalDateTime creadoEn;
}
