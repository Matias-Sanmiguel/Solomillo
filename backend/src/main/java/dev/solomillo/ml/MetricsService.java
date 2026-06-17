package dev.solomillo.ml;

import dev.solomillo.repository.PrediccionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsService {

    private static final int BINS = 10;

    private final PrediccionRepository repo;

    public MetricsService(PrediccionRepository repo) {
        this.repo = repo;
    }

    private static double[] probs(Prediccion p) {
        return new double[]{p.getProbLocal(), p.getProbEmpate(), p.getProbVisitante()};
    }

    private static int argmax(double[] v) {
        int idx = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[idx]) idx = i;
        return idx;
    }

    private List<Prediccion> resueltas(int version) {
        return repo.findByModeloVersion(version).stream()
                .filter(p -> p.getResultadoReal() != null)
                .sorted(Comparator.comparing(Prediccion::getPredichoEn))
                .toList();
    }

    public Map<String, Object> metricas(int version) {
        List<Prediccion> ps = resueltas(version);
        int n = ps.size();
        if (n == 0) return Map.of("version", version, "n", 0);

        double correct = 0, logLoss = 0, brier = 0;
        List<Map<String, Object>> serie = new ArrayList<>();
        double acAcum = 0;
        int i = 0;
        for (Prediccion p : ps) {
            double[] v = probs(p);
            int real = p.getResultadoReal();
            boolean ok = argmax(v) == real;
            if (ok) { correct++; acAcum++; }
            logLoss += -Math.log(Math.max(1e-15, v[real]));
            for (int k = 0; k < v.length; k++) brier += Math.pow(v[k] - (k == real ? 1 : 0), 2);
            i++;
            serie.add(Map.of("i", i, "accuracy", round(acAcum / i)));
        }
        return Map.of(
                "version", version,
                "n", n,
                "accuracy", round(correct / n),
                "log_loss", round(logLoss / n),
                "brier", round(brier / n),
                "serie", serie);
    }

    public Map<String, Object> calibracion(int version) {
        List<Prediccion> ps = resueltas(version);
        double[] sumaConf = new double[BINS];
        double[] aciertos = new double[BINS];
        int[] conteo = new int[BINS];

        for (Prediccion p : ps) {
            double[] v = probs(p);
            int pred = argmax(v);
            double conf = v[pred];
            int bin = Math.min(BINS - 1, (int) (conf * BINS));
            sumaConf[bin] += conf;
            if (pred == p.getResultadoReal()) aciertos[bin]++;
            conteo[bin]++;
        }

        List<Map<String, Object>> bins = new ArrayList<>();
        for (int b = 0; b < BINS; b++) {
            if (conteo[b] == 0) continue;
            bins.add(Map.of(
                    "confianza", round(sumaConf[b] / conteo[b]),
                    "precision", round(aciertos[b] / conteo[b]),
                    "n", conteo[b]));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", version);
        out.put("bins", bins);
        return out;
    }

    private static double round(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }
}
