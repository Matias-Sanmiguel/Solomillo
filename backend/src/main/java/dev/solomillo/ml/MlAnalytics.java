package dev.solomillo.ml;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.rankings.Posicion;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PosicionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import weka.classifiers.functions.Logistic;
import weka.core.Instances;
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
    private final PartidoRepository partidoRepo;

    public MlAnalytics(PosicionRepository p, ModeloPredictivoRepository m, PartidoRepository pr) {
        this.posicionRepo = p;
        this.modeloRepo = m;
        this.partidoRepo = pr;
    }

    /**
     * Proyecta la tabla final del torneo: a los puntos actuales de cada equipo le suma los
     * puntos esperados de sus partidos pendientes, estimados con el modelo {@code resultado_partido}.
     * Usa las mismas 9 features de {@link FeatureExtractor} con las que se entrena y predice el modelo.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> proyectar(Long torneoId) throws Exception {
        ModeloPredictivo modelo = modeloRepo.findByNombreAndActivoTrue("resultado_partido")
                .orElseThrow(() -> new IllegalStateException("Sin modelo activo."));
        Logistic clf = (Logistic) SerializationHelper.read(modelo.getRuta());
        Instances header = FeatureExtractor.buildHeader();

        // Contexto historico para las features (forma reciente, h2h, etc.).
        List<Partido> finalizados = partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.FINALIZADO);

        // Punto de partida: puntos actuales por equipo segun la tabla de posiciones.
        List<Posicion> tabla = posicionRepo.findByTorneoIdOrderByPuntosDescGolesFavorDesc(torneoId);
        Map<Long, Integer> actuales = new HashMap<>();
        Map<Long, Double> esperados = new HashMap<>();
        for (Posicion fila : tabla) {
            actuales.put(fila.getEquipoId(), fila.getPuntos());
            esperados.put(fila.getEquipoId(), (double) fila.getPuntos());
        }

        // Suma de puntos esperados de los partidos aun no finalizados del torneo.
        List<Partido> pendientes = partidoRepo.findByEstadoNotOrderByFechaHoraAsc(EstadoPartido.FINALIZADO);
        for (Partido partido : pendientes) {
            if (partido.getTorneo() == null || !torneoId.equals(partido.getTorneo().getId())) continue;
            double[] probs = clf.distributionForInstance(
                    FeatureExtractor.instancia(partido, finalizados, header));
            // probs: [0]=gana local, [1]=empate, [2]=gana visitante.
            acumular(esperados, partido.getEquipoLocal().getId(), 3 * probs[0] + probs[1]);
            acumular(esperados, partido.getEquipoVisitante().getId(), 3 * probs[2] + probs[1]);
        }

        var result = new ArrayList<Map<String, Object>>();
        for (Long equipoId : actuales.keySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("equipo_id", equipoId);
            row.put("puntos_actuales", actuales.get(equipoId));
            row.put("puntos_esperados", Math.round(esperados.get(equipoId) * 1000.0) / 1000.0);
            result.add(row);
        }
        result.sort(Comparator.<Map<String, Object>, Double>comparing(
                m2 -> (Double) m2.get("puntos_esperados")).reversed());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).put("posicion_proyectada", i + 1);
        }
        return result;
    }

    private static void acumular(Map<Long, Double> esperados, Long equipoId, double delta) {
        if (equipoId != null && esperados.containsKey(equipoId)) {
            esperados.merge(equipoId, delta, Double::sum);
        }
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
