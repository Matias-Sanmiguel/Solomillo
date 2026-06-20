package dev.solomillo.sim;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.events.MotorProcesamiento;
import dev.solomillo.ml.EloService;
import dev.solomillo.ml.MlPredictor;
import dev.solomillo.ml.ResultadoService;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.stats.EstadisticaJugador;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Motor de simulación: cierra un partido pendiente como si realmente hubiera ocurrido.
 * <p>
 * No crea lógica paralela. El resultado lo decide el modelo ML (probabilidades local/empate/
 * visitante), el marcador se genera con Poisson coherente con ese signo, y el partido se cierra
 * por exactamente el mismo flujo que un partido real:
 * <ul>
 *   <li>eventos {@code gol}/{@code tarjeta}/{@code fin_partido} → {@link MotorProcesamiento}
 *       (calculadores de estadística + {@code RankingsService} + publisher en tiempo real);</li>
 *   <li>{@link ResultadoService#registrar} → Elo, {@code Prediccion.resultadoReal} y prode.</li>
 * </ul>
 * Así los partidos simulados quedan indistinguibles de los reales ya cargados.
 */
@Service
public class SimulacionService {

    private static final double HOME_ADV = 100.0;
    private static final int MAX_REINTENTOS = 40;

    private final PartidoRepository partidoRepo;
    private final MlPredictor predictor;
    private final ResultadoService resultados;
    private final MotorProcesamiento motor;
    private final JugadorRepository jugadorRepo;
    private final EstadisticaJugadorRepository ejRepo;

    public SimulacionService(PartidoRepository partidoRepo, MlPredictor predictor,
                             ResultadoService resultados, MotorProcesamiento motor,
                             JugadorRepository jugadorRepo, EstadisticaJugadorRepository ejRepo) {
        this.partidoRepo = partidoRepo;
        this.predictor = predictor;
        this.resultados = resultados;
        this.motor = motor;
        this.jugadorRepo = jugadorRepo;
        this.ejRepo = ejRepo;
    }

    /** True si el partido puede simularse: pendiente y con ambos rivales definidos. */
    public static boolean simulable(Partido p) {
        return p != null
                && p.getEstado() != EstadoPartido.FINALIZADO
                && p.getEquipoLocal() != null
                && p.getEquipoVisitante() != null;
    }

    /**
     * Simula un único partido y devuelve un resumen ({@code partido_id}, equipos, marcador,
     * goleadores, figura, probabilidades usadas). Reutiliza un {@link Random} sembrado para
     * permitir reproducibilidad dentro de una simulación de torneo.
     */
    @Transactional
    public Map<String, Object> simularPartido(Long partidoId, Random rng) {
        Partido p = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new IllegalArgumentException("Partido inexistente"));
        if (!simulable(p)) {
            throw new IllegalStateException("El partido no es simulable (finalizado o sin rival definido).");
        }
        return ejecutar(p, rng != null ? rng : new Random());
    }

    /** Probabilidades del modelo activo: [local, empate, visitante]. */
    private double[] probabilidades(Partido p) {
        try {
            Map<String, Object> salida = predictor.tablero(p.getId());
            @SuppressWarnings("unchecked")
            Map<String, Number> probs = (Map<String, Number>) salida.get("probabilidades");
            return new double[]{
                    probs.getOrDefault("local", 0.34).doubleValue(),
                    probs.getOrDefault("empate", 0.33).doubleValue(),
                    probs.getOrDefault("visitante", 0.33).doubleValue()
            };
        } catch (IllegalStateException e) {
            throw e; // sin modelo entrenado: lo propaga el controlador
        } catch (Exception e) {
            // Si el modelo falla por features, caemos a un prior por Elo para no abortar el torneo.
            return prioridadElo(p);
        }
    }

    private Map<String, Object> ejecutar(Partido p, Random rng) {
        // Un partido EN_VIVO ya tiene su marcador, eventos y posiciones contabilizados por la
        // carga inicial (todo salvo el Elo). Re-simularlo duplicaría las posiciones; en su lugar
        // lo cerramos a su marcador actual: aplica Elo + Prediccion.resultadoReal + prode una vez.
        if (p.getEstado() == EstadoPartido.EN_VIVO) {
            int gl = p.getGolesLocal() != null ? p.getGolesLocal() : 0;
            int gv = p.getGolesVisitante() != null ? p.getGolesVisitante() : 0;
            resultados.registrar(p.getId(), gl, gv);
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("partido_id", p.getId());
            out.put("ronda", p.getRonda() != null ? p.getRonda().name() : null);
            out.put("grupo", p.getGrupo());
            out.put("local", p.getEquipoLocal().getNombre());
            out.put("visitante", p.getEquipoVisitante().getNombre());
            out.put("goles_local", gl);
            out.put("goles_visitante", gv);
            out.put("goleadores", java.util.List.of());
            out.put("figura", Map.of());
            out.put("en_vivo_cerrado", true);
            return out;
        }

        double[] probs = probabilidades(p);
        // En eliminatorias no puede haber empate: la masa del empate se reparte a local/visitante
        // (en proporción a sus probabilidades) para garantizar un ganador que avance en el cuadro.
        boolean permiteEmpate = p.getRonda() == null || p.getRonda() == dev.solomillo.domain.Ronda.GRUPOS;
        if (!permiteEmpate) probs = sinEmpate(probs);
        int signo = muestrear(probs, rng);            // 0 local, 1 empate, 2 visitante
        int[] marcador = generarMarcador(p, signo, rng);
        int gl = marcador[0], gv = marcador[1];

        List<Jugador> jl = jugadorRepo.findByEquipoId(p.getEquipoLocal().getId());
        List<Jugador> jv = jugadorRepo.findByEquipoId(p.getEquipoVisitante().getId());
        Long torneoId = p.getTorneo().getId();

        List<Map<String, Object>> goleadores = new ArrayList<>();
        int minuto = 0;
        for (int g = 0; g < gl && !jl.isEmpty(); g++) {
            minuto = Math.min(90, minuto + Math.max(1, rng.nextInt(12)));
            goleadores.add(anotar(p, jl, torneoId, minuto, rng));
        }
        for (int g = 0; g < gv && !jv.isEmpty(); g++) {
            minuto = Math.min(90, minuto + Math.max(1, rng.nextInt(12)));
            goleadores.add(anotar(p, jv, torneoId, minuto, rng));
        }

        tarjetas(p, jl, jv, rng);

        // Cierra el partido por el flujo real: rankings.cerrarPartido cuenta V/E/D de los goles.
        motor.procesar(new EventoInterno("fin_partido", p.getId(), 90, null, Map.of()), "simulacion");

        // Elo + Prediccion.resultadoReal + prode (mismo servicio que un resultado real cargado a mano).
        resultados.registrar(p.getId(), gl, gv);

        Map<String, Object> figura = elegirFigura(goleadores, jl, jv, rng);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("partido_id", p.getId());
        out.put("ronda", p.getRonda() != null ? p.getRonda().name() : null);
        out.put("grupo", p.getGrupo());
        out.put("local_id", p.getEquipoLocal().getId());
        out.put("visitante_id", p.getEquipoVisitante().getId());
        out.put("local", p.getEquipoLocal().getNombre());
        out.put("visitante", p.getEquipoVisitante().getNombre());
        out.put("goles_local", gl);
        out.put("goles_visitante", gv);
        out.put("goleadores", goleadores);
        out.put("figura", figura);
        out.put("probabilidades", Map.of(
                "local", redondear(probs[0]),
                "empate", redondear(probs[1]),
                "visitante", redondear(probs[2])));
        return out;
    }

    /** Emite el gol por el motor (estadísticas + ranking reales) y acredita una asistencia ~65%. */
    private Map<String, Object> anotar(Partido p, List<Jugador> plantel, Long torneoId,
                                       int minuto, Random rng) {
        Jugador autor = elegirGoleador(plantel, torneoId, rng);
        motor.procesar(new EventoInterno("gol", p.getId(), minuto, autor.getId(), Map.of()), "simulacion");
        Long asistenteId = asistencia(plantel, autor.getId(), torneoId, rng);
        Map<String, Object> g = new java.util.LinkedHashMap<>();
        g.put("jugador_id", autor.getId());
        g.put("nombre", autor.getNombre());
        g.put("minuto", minuto);
        g.put("asistencia_id", asistenteId);
        return g;
    }

    /** Tarjetas: mayoría amarillas, rojas esporádicas; por el mismo flujo que un partido real. */
    private void tarjetas(Partido p, List<Jugador> jl, List<Jugador> jv, Random rng) {
        int n = rng.nextInt(5);
        for (int t = 0; t < n; t++) {
            List<Jugador> js = rng.nextBoolean() ? jl : jv;
            if (js.isEmpty()) continue;
            Long jug = js.get(rng.nextInt(js.size())).getId();
            String tipo = rng.nextInt(10) < 2 ? "RED_CARD" : "YELLOW_CARD";
            motor.procesar(new EventoInterno("tarjeta", p.getId(), 10 + rng.nextInt(80), jug,
                    Map.of("eventType", tipo)), "simulacion");
        }
    }

    /** Asistencia acreditada a un compañero distinto del goleador (incrementa la métrica). */
    private Long asistencia(List<Jugador> plantel, Long goleador, Long torneoId, Random rng) {
        if (plantel.size() < 2 || rng.nextDouble() >= 0.65) return null;
        Long asistente;
        do { asistente = plantel.get(rng.nextInt(plantel.size())).getId(); } while (asistente.equals(goleador));
        final Long jId = asistente;
        var stat = ejRepo.findByJugadorIdAndTorneoIdAndMetrica(jId, torneoId, "asistencias")
                .orElseGet(() -> {
                    var s = new EstadisticaJugador();
                    s.setJugadorId(jId); s.setTorneoId(torneoId); s.setMetrica("asistencias");
                    return s;
                });
        stat.setValor(stat.getValor() + 1);
        ejRepo.save(stat);
        return jId;
    }

    // --- Selección de jugadores: posición × rendimiento previo (no aleatorio absurdo) ---

    private Jugador elegirGoleador(List<Jugador> plantel, Long torneoId, Random rng) {
        double total = 0;
        double[] pesos = new double[plantel.size()];
        for (int i = 0; i < plantel.size(); i++) {
            Jugador j = plantel.get(i);
            double golesPrevios = ejRepo.findByJugadorIdAndTorneoIdAndMetrica(j.getId(), torneoId, "goles")
                    .map(EstadisticaJugador::getValor).orElse(0.0);
            pesos[i] = pesoPosicion(j.getPosicion()) * (1.0 + golesPrevios);
            total += pesos[i];
        }
        double r = rng.nextDouble() * total;
        for (int i = 0; i < plantel.size(); i++) {
            r -= pesos[i];
            if (r <= 0) return plantel.get(i);
        }
        return plantel.get(plantel.size() - 1);
    }

    private double pesoPosicion(String posicion) {
        if (posicion == null) return 1.0;
        return switch (posicion) {
            case "Delantero" -> 6.0;
            case "Mediocampista" -> 3.0;
            default -> 1.0; // Defensor / Arquero
        };
    }

    /** Figura: preferentemente el autor de más goles; si fue 0-0, un jugador ponderado por posición. */
    private Map<String, Object> elegirFigura(List<Map<String, Object>> goleadores,
                                             List<Jugador> jl, List<Jugador> jv, Random rng) {
        if (!goleadores.isEmpty()) {
            Map<Long, Integer> conteo = new java.util.HashMap<>();
            Map<Long, String> nombre = new java.util.HashMap<>();
            for (Map<String, Object> g : goleadores) {
                Long id = (Long) g.get("jugador_id");
                conteo.merge(id, 1, Integer::sum);
                nombre.put(id, (String) g.get("nombre"));
            }
            Long fig = conteo.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            return Map.of("jugador_id", fig, "nombre", nombre.get(fig));
        }
        List<Jugador> todos = new ArrayList<>(jl); todos.addAll(jv);
        if (todos.isEmpty()) return Map.of();
        Jugador f = todos.get(rng.nextInt(todos.size()));
        return Map.of("jugador_id", f.getId(), "nombre", f.getNombre());
    }

    // --- Generación de marcador coherente con el signo muestreado del modelo ---

    /** Reparte la probabilidad de empate sobre local/visitante (proporcional) → sin empate posible. */
    private double[] sinEmpate(double[] probs) {
        double base = probs[0] + probs[2];
        if (base <= 0) return new double[]{0.5, 0.0, 0.5};
        double escala = (probs[0] + probs[1] + probs[2]) / base;
        return new double[]{probs[0] * escala, 0.0, probs[2] * escala};
    }

    private int muestrear(double[] probs, Random rng) {
        double suma = probs[0] + probs[1] + probs[2];
        double r = rng.nextDouble() * (suma > 0 ? suma : 1);
        if (r < probs[0]) return 0;
        if (r < probs[0] + probs[1]) return 1;
        return 2;
    }

    private int[] generarMarcador(Partido p, int signo, Random rng) {
        double eLocal = EloService.baseElo(p.getEquipoLocal());
        double eVisit = EloService.baseElo(p.getEquipoVisitante());
        double ventaja = p.isNeutral() ? 0.0 : HOME_ADV;
        double expHome = 1.0 / (1.0 + Math.pow(10, (eVisit - eLocal - ventaja) / 400.0));
        double muLocal = Math.max(0.25, 1.35 + 1.6 * (expHome - 0.5));
        double muVisit = Math.max(0.25, 1.35 - 1.6 * (expHome - 0.5));

        for (int intento = 0; intento < MAX_REINTENTOS; intento++) {
            int gl = poisson(rng, muLocal);
            int gv = poisson(rng, muVisit);
            int s = Integer.compare(gl, gv) > 0 ? 0 : (gl == gv ? 1 : 2);
            if (s == signo) return new int[]{gl, gv};
        }
        // Fallback determinista mínimo si Poisson no acompañó al signo muestreado.
        return switch (signo) {
            case 0 -> new int[]{1, 0};
            case 2 -> new int[]{0, 1};
            default -> new int[]{1, 1};
        };
    }

    private int poisson(Random rng, double mu) {
        double l = Math.exp(-mu);
        int k = 0;
        double pr = 1.0;
        do { k++; pr *= rng.nextDouble(); } while (pr > l);
        return Math.min(k - 1, 7);
    }

    private double[] prioridadElo(Partido p) {
        double eLocal = EloService.baseElo(p.getEquipoLocal());
        double eVisit = EloService.baseElo(p.getEquipoVisitante());
        double ventaja = p.isNeutral() ? 0.0 : HOME_ADV;
        double expHome = 1.0 / (1.0 + Math.pow(10, (eVisit - eLocal - ventaja) / 400.0));
        double empate = 0.27;
        double local = (1 - empate) * expHome;
        double visit = (1 - empate) * (1 - expHome);
        return new double[]{local, empate, visit};
    }

    private double redondear(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
