package dev.solomillo.prode;

import dev.solomillo.domain.Partido;
import dev.solomillo.users.Usuario;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "pronosticos",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "partido_id"}))
public class Pronostico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_id")
    private Partido partido;

    @Enumerated(EnumType.STRING)
    private Signo signo;

    // Resultado exacto opcional
    private Integer golesLocal;
    private Integer golesVisitante;

    // null mientras el pronóstico no fue resuelto
    private Integer puntos;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;
}
