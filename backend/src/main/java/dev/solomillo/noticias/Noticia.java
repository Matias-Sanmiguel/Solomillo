package dev.solomillo.noticias;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Noticia generada automáticamente a partir de los datos deportivos del sistema
 * (resultados, predicciones, estadísticas, Elo). Se persiste para tener IDs
 * estables, poder filtrar por categoría y conservar récords/cambios históricos.
 *
 * La imagen NO se almacena: se guardan los identificadores ({@code partidoId},
 * escudos vía equipos, {@code marcador}) y el frontend compone la imagen.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "noticias")
public class Noticia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String subtitulo;

    @Enumerated(EnumType.STRING)
    private CategoriaNoticia categoria;

    @Column(length = 2000)
    private String resumen;

    private LocalDateTime fecha;

    /** Mayor relevancia => va más arriba en el feed; la máxima es el hero. */
    private int relevancia;

    /** REAL (Fase 1) o SIMULADO (Fase 2, proyectado por IA). */
    private String origen = "REAL";

    /** Tipo de imagen a componer en el frontend: PARTIDO / EQUIPO / JUGADOR / RANKING. */
    private String imagenTipo = "PARTIDO";

    // Referencias para que el frontend resuelva escudos/nombres y arme la imagen.
    private Long partidoId;
    private Long equipoLocalId;
    private Long equipoVisitanteId;
    private Long jugadorId;
    private String marcador;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "noticia_tags", joinColumns = @JoinColumn(name = "noticia_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    /**
     * Clave de deduplicación idempotente (p.ej. "GOLEADA:partido=123"). Permite
     * regenerar el feed sin duplicar: si ya existe una noticia con esta clave,
     * se actualiza en lugar de crear otra.
     */
    @Column(unique = true)
    private String claveNatural;
}
