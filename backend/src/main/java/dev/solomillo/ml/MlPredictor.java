package dev.solomillo.ml;

import dev.solomillo.domain.Partido;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PosicionRepository;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.rankings.Posicion;
import org.springframework.stereotype.Service;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.LinearRegression;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MlPredictor {

    private static final String[] LABELS = {"local", "empate", "visitante"};

    private final ModeloPredictivoRepository modeloRepo;
    private final PartidoRepository partidoRepo;
    private final PosicionRepository posicionRepo;
    private final EstadisticaJugadorRepository ejRepo;

    public MlPredictor(ModeloPredictivoRepository m, PartidoRepository p,
                       PosicionRepository pos, EstadisticaJugadorRepository ej) {
        this.modeloRepo = m; this.partidoRepo = p;
        this.posicionRepo = pos; this.ejRepo = ej;
    }

    public Map<String, Object> predecirResultado(Long partidoId) throws Exception {
        ModeloPredictivo modelo = modeloRepo.findByNombreAndActivoTrue("resultado_partido")
                .orElseThrow(() -> new IllegalStateException("Sin modelo activo. Entrenar primero."));

        Partido partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new IllegalArgumentException("Partido inexistente"));

        Long torneoId = partido.getTorneo().getId();
        double[] gfgc = posGoles(torneoId, partido.getEquipoLocal().getId());
        double[] gfgcV = posGoles(torneoId, partido.getEquipoVisitante().getId());

        Instances header = DatasetGenerator.buildHeader();
        var inst = new DenseInstance(1.0, new double[]{gfgc[0], gfgc[1], gfgcV[0], gfgcV[1], 0});
        inst.setDataset(header);

        Logistic clf = (Logistic) SerializationHelper.read(modelo.getRuta());
        double[] probs = clf.distributionForInstance(inst);

        Map<String, Double> probabilidades = new LinkedHashMap<>();
        for (int i = 0; i < probs.length && i < LABELS.length; i++) {
            probabilidades.put(LABELS[i], Math.round(probs[i] * 10000.0) / 10000.0);
        }
        return Map.of("modelo_version", modelo.getVersion(), "probabilidades", probabilidades);
    }

    public Map<String, Object> rendimientoJugador(Long jugadorId) throws Exception {
        ModeloPredictivo modelo = modeloRepo.findByNombreAndActivoTrue("rendimiento_jugador")
                .orElseThrow(() -> new IllegalStateException("Sin modelo activo para rendimiento."));

        double goles = ejRepo.findByJugadorIdAndMetrica(jugadorId, "goles")
                .map(s -> s.getValor()).orElse(0.0);

        Instances header = DatasetGenerator.buildRegressionHeader();
        var inst = new DenseInstance(1.0, new double[]{goles, 0});
        inst.setDataset(header);

        LinearRegression reg = (LinearRegression) SerializationHelper.read(modelo.getRuta());
        double rating = Math.min(10, Math.max(1, reg.classifyInstance(inst)));

        return Map.of("modelo_version", modelo.getVersion(), "jugador_id", jugadorId,
                "goles", goles, "rating_esperado", Math.round(rating * 100.0) / 100.0);
    }

    private double[] posGoles(Long torneoId, Long equipoId) {
        return posicionRepo.findByTorneoIdAndEquipoId(torneoId, equipoId)
                .map(p -> new double[]{p.getGolesFavor(), p.getGolesContra()})
                .orElse(new double[]{0, 0});
    }
}
