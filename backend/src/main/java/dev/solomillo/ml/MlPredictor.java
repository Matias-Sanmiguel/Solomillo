package dev.solomillo.ml;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PrediccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.Logistic;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MlPredictor {

    private static final String[] LABELS = {"local", "empate", "visitante"};

    private final ModeloPredictivoRepository modeloRepo;
    private final PartidoRepository partidoRepo;
    private final PrediccionRepository prediccionRepo;
    private final EstadisticaJugadorRepository ejRepo;

    public MlPredictor(ModeloPredictivoRepository m, PartidoRepository p,
                       PrediccionRepository pr, EstadisticaJugadorRepository ej) {
        this.modeloRepo = m;
        this.partidoRepo = p;
        this.prediccionRepo = pr;
        this.ejRepo = ej;
    }

    @Transactional
    public Map<String, Object> predecirResultado(Long partidoId) throws Exception {
        Partido partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new IllegalArgumentException("Partido inexistente"));
        double[] probs = probabilidades(partido);
        ModeloPredictivo modelo = modeloActivo();
        persistir(partido, modelo.getVersion(), probs);
        return salida(partido, modelo.getVersion(), probs);
    }

    /** Probabilidades sin persistir, para el board (varios partidos). */
    @Transactional(readOnly = true)
    public Map<String, Object> tablero(Long partidoId) throws Exception {
        Partido partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new IllegalArgumentException("Partido inexistente"));
        ModeloPredictivo modelo = modeloActivo();
        double[] probs = probabilidades(partido, modelo);
        return salida(partido, modelo.getVersion(), probs);
    }

    private double[] probabilidades(Partido partido) throws Exception {
        return probabilidades(partido, modeloActivo());
    }

    private double[] probabilidades(Partido partido, ModeloPredictivo modelo) throws Exception {
        List<Partido> finalizados = partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.FINALIZADO);
        Instances header = FeatureExtractor.buildHeader();
        DenseInstance inst = FeatureExtractor.instancia(partido, finalizados, header);
        Logistic clf = (Logistic) SerializationHelper.read(modelo.getRuta());
        return clf.distributionForInstance(inst);
    }

    private ModeloPredictivo modeloActivo() {
        return modeloRepo.findByNombreAndActivoTrue("resultado_partido")
                .orElseThrow(() -> new IllegalStateException("Sin modelo activo. Entrenar primero."));
    }

    private void persistir(Partido partido, int version, double[] probs) {
        var pred = new Prediccion();
        pred.setPartido(partido);
        pred.setModeloVersion(version);
        pred.setProbLocal(probs[0]);
        pred.setProbEmpate(probs.length > 1 ? probs[1] : 0);
        pred.setProbVisitante(probs.length > 2 ? probs[2] : 0);
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            pred.setResultadoReal((int) FeatureExtractor.etiqueta(partido));
        }
        prediccionRepo.save(pred);
    }

    private Map<String, Object> salida(Partido partido, int version, double[] probs) {
        Map<String, Double> probabilidades = new LinkedHashMap<>();
        for (int i = 0; i < probs.length && i < LABELS.length; i++) {
            probabilidades.put(LABELS[i], Math.round(probs[i] * 10000.0) / 10000.0);
        }
        return Map.of(
                "partido_id", partido.getId(),
                "modelo_version", version,
                "probabilidades", probabilidades);
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
}
