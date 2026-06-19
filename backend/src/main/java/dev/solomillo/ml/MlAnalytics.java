package dev.solomillo.ml;

import dev.solomillo.rankings.Posicion;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PosicionRepository;
import org.springframework.stereotype.Service;
import weka.classifiers.functions.Logistic;
import weka.core.DenseInstance;
import weka.core.SerializationHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MlAnalytics {

    private final PosicionRepository posicionRepo;
    private final ModeloPredictivoRepository modeloRepo;

    public MlAnalytics(PosicionRepository p, ModeloPredictivoRepository m) {
        this.posicionRepo = p; this.modeloRepo = m;
    }

    public List<Map<String, Object>> proyectar(Long torneoId) throws Exception {
        ModeloPredictivo modelo = modeloRepo.findByNombreAndActivoTrue("resultado_partido")
                .orElseThrow(() -> new IllegalStateException("Sin modelo activo."));
        Logistic clf = (Logistic) SerializationHelper.read(modelo.getRuta());
        var header = FeatureExtractor.buildHeader();

        List<Posicion> filas = posicionRepo.findByTorneoIdOrderByPuntosDescGolesFavorDesc(torneoId);
        double avgGf = filas.stream().mapToInt(Posicion::getGolesFavor).average().orElse(0);
        double avgGc = filas.stream().mapToInt(Posicion::getGolesContra).average().orElse(0);

        var result = new ArrayList<Map<String, Object>>();
        for (Posicion f : filas) {
            var inst = new DenseInstance(1.0, new double[]{f.getGolesFavor(), f.getGolesContra(), avgGf, avgGc, 0});
            inst.setDataset(header);
            double[] probs = clf.distributionForInstance(inst);
            double xpts = 3 * probs[0] + 1 * probs[1];
            Map<String, Object> row = new HashMap<>();
            row.put("equipo_id", f.getEquipoId());
            row.put("puntos_actuales", f.getPuntos());
            row.put("puntos_esperados", Math.round(xpts * 1000.0) / 1000.0);
            result.add(row);
        }
        result.sort(Comparator.<Map<String, Object>, Double>comparing(
                m2 -> (Double) m2.get("puntos_esperados")).reversed());
        for (int i = 0; i < result.size(); i++) {
            var m2 = new java.util.HashMap<>(result.get(i));
            m2.put("posicion_proyectada", i + 1);
            result.set(i, m2);
        }
        return result;
    }

    public List<Map<String, Object>> tendencias(Long torneoId) {
        return posicionRepo.findByTorneoIdOrderByPuntosDescGolesFavorDesc(torneoId).stream()
                .map(f -> {
                    int dif = f.getGolesFavor() - f.getGolesContra();
                    String estado = dif >= 2 ? "ofensiva fuerte" : dif <= -2 ? "defensiva débil" : "estable";
                    Map<String, Object> m = new HashMap<>();
                    m.put("equipo_id", f.getEquipoId());
                    m.put("diferencia", dif);
                    m.put("tendencia", estado);
                    return m;
                })
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(
                        m -> (Integer) m.get("diferencia")).reversed())
                .toList();
    }
}
