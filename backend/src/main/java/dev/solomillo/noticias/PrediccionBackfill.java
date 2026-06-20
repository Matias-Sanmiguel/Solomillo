package dev.solomillo.noticias;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.ml.MlPredictor;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PrediccionRepository;
import dev.solomillo.repository.TorneoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Genera (si faltan) las predicciones de los partidos del Mundial ya finalizados,
 * para que los detectores basados en predicción ({@code ResultadoInesperado},
 * {@code Batacazo}) y la sección "Lo que predijo la IA" tengan datos.
 *
 * Reusa {@link MlPredictor#predecirResultado} (persiste la predicción y fija el
 * resultado real). Si no hay modelo entrenado, no hace nada: las predicciones se
 * completan cuando el usuario entrena el modelo y regenera el feed.
 */
@Component
public class PrediccionBackfill {

    private static final Logger log = LoggerFactory.getLogger(PrediccionBackfill.class);

    private final PartidoRepository partidoRepo;
    private final PrediccionRepository prediccionRepo;
    private final ModeloPredictivoRepository modeloRepo;
    private final TorneoRepository torneoRepo;
    private final MlPredictor predictor;

    public PrediccionBackfill(PartidoRepository partidoRepo, PrediccionRepository prediccionRepo,
                              ModeloPredictivoRepository modeloRepo, TorneoRepository torneoRepo,
                              MlPredictor predictor) {
        this.partidoRepo = partidoRepo;
        this.prediccionRepo = prediccionRepo;
        this.modeloRepo = modeloRepo;
        this.torneoRepo = torneoRepo;
        this.predictor = predictor;
    }

    /** Backfill de predicciones para los partidos finalizados del Mundial. Devuelve cuántas creó. */
    public int backfillMundial() {
        if (modeloRepo.findByNombre("resultado_partido").isEmpty()) return 0;
        Long torneoId = torneoRepo.findByNombreAndTemporada(NoticiaService.MUNDIAL, NoticiaService.TEMPORADA)
                .map(Torneo::getId).orElse(null);
        if (torneoId == null) return 0;

        int creadas = 0;
        var finalizados = partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.FINALIZADO).stream()
                .filter(p -> p.getTorneo() != null && torneoId.equals(p.getTorneo().getId()))
                .filter(p -> p.getEquipoLocal() != null && p.getEquipoVisitante() != null)
                .sorted(Comparator.comparing(Partido::getFechaHora, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (Partido p : finalizados) {
            if (prediccionRepo.findFirstByPartidoIdOrderByPredichoEnDesc(p.getId()).isPresent()) continue;
            try {
                predictor.predecirResultado(p.getId());
                creadas++;
            } catch (Exception e) {
                log.warn("No se pudo predecir partido {}: {}", p.getId(), e.getMessage());
            }
        }
        return creadas;
    }
}
