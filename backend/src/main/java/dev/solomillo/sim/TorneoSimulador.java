package dev.solomillo.sim;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Ronda;
import dev.solomillo.domain.Torneo;
import dev.solomillo.ml.MlTrainer;
import dev.solomillo.ml.ModeloPredictivo;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.TorneoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Orquesta las simulaciones de varios partidos: una fecha completa o el Mundial entero.
 * <p>
 * No abre transacción propia a propósito: invoca a {@link SimulacionService#simularPartido}
 * (un partido por transacción, para que el progreso persista aunque uno falle) y a
 * {@link BracketService#avanzarCuadro} (su propia transacción) entre tandas, de modo que los
 * cruces de llave se completen a medida que se cierran las rondas.
 */
@Service
public class TorneoSimulador {

    private static final String TORNEO_MUNDIAL = "Copa Mundial FIFA 2026";
    private static final String TEMPORADA = "2026";
    private static final int MAX_ITERACIONES = 200; // cota de seguridad anti-bucle

    private final SimulacionService simulacion;
    private final BracketService bracket;
    private final MlTrainer trainer;
    private final PartidoRepository partidoRepo;
    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;

    public TorneoSimulador(SimulacionService simulacion, BracketService bracket, MlTrainer trainer,
                           PartidoRepository partidoRepo, TorneoRepository torneoRepo,
                           EquipoRepository equipoRepo) {
        this.simulacion = simulacion;
        this.bracket = bracket;
        this.trainer = trainer;
        this.partidoRepo = partidoRepo;
        this.torneoRepo = torneoRepo;
        this.equipoRepo = equipoRepo;
    }

    private Long mundialId() {
        Torneo m = torneoRepo.findByNombreAndTemporada(TORNEO_MUNDIAL, TEMPORADA)
                .orElseThrow(() -> new IllegalStateException("No existe el torneo del Mundial 2026."));
        return m.getId();
    }

    private List<Partido> pendientesSimulables() {
        return partidoRepo.findByTorneo_IdOrderByFechaHoraAsc(mundialId()).stream()
                .filter(SimulacionService::simulable)
                .toList();
    }

    /**
     * Simula la próxima jornada: todos los partidos pendientes con la fecha más cercana. Antes y
     * después actualiza el cuadro de eliminatorias. {@code reentrenar} dispara un reentrenamiento
     * del modelo de resultado al finalizar.
     */
    public Map<String, Object> simularFecha(boolean reentrenar) {
        bracket.avanzarCuadro();
        List<Partido> simulables = pendientesSimulables();
        if (simulables.isEmpty()) {
            return Map.of("mensaje", "No hay partidos pendientes para simular.", "partidos", List.of());
        }
        LocalDate jornada = simulables.get(0).getFechaHora().toLocalDate();
        List<Partido> deLaJornada = simulables.stream()
                .filter(p -> p.getFechaHora().toLocalDate().equals(jornada))
                .toList();

        Random rng = new Random();
        List<Map<String, Object>> resultados = new ArrayList<>();
        for (Partido p : deLaJornada) resultados.add(simularUno(p.getId(), rng));
        bracket.avanzarCuadro();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jornada", jornada.toString());
        out.put("simulados", resultados.size());
        out.put("partidos", resultados);
        out.put("reentrenado", reentrenar ? reentrenar() : null);
        return out;
    }

    /**
     * Simula todos los partidos restantes hasta completar el torneo (grupos → llave → final),
     * avanzando el cuadro entre tandas. Devuelve campeón, subcampeón, tercero y el detalle.
     */
    public Map<String, Object> simularMundial(boolean reentrenar) {
        Random rng = new Random();
        List<Map<String, Object>> resultados = new ArrayList<>();
        int iteraciones = 0;
        while (iteraciones++ < MAX_ITERACIONES) {
            bracket.avanzarCuadro();
            List<Partido> simulables = pendientesSimulables();
            if (simulables.isEmpty()) break;
            for (Partido p : simulables) resultados.add(simularUno(p.getId(), rng));
        }
        bracket.avanzarCuadro();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("simulados", resultados.size());
        out.putAll(podio());
        out.put("partidos", resultados);
        out.put("reentrenado", reentrenar ? reentrenar() : null);
        return out;
    }

    /** Campeón/subcampeón (de la FINAL) y tercer puesto, si esas rondas ya están resueltas. */
    private Map<String, Object> podio() {
        Long id = mundialId();
        Map<String, Object> podio = new LinkedHashMap<>();
        finalizado(id, Ronda.FINAL).ifPresent(f -> {
            podio.put("campeon", nombre(ganadorId(f)));
            podio.put("subcampeon", nombre(perdedorId(f)));
        });
        finalizado(id, Ronda.TERCER_PUESTO).ifPresent(t -> podio.put("tercero", nombre(ganadorId(t))));
        return podio;
    }

    private Optional<Partido> finalizado(Long torneoId, Ronda ronda) {
        return partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, ronda).stream()
                .filter(p -> p.getEstado() == EstadoPartido.FINALIZADO)
                .max(Comparator.comparing(Partido::getFechaHora));
    }

    private Map<String, Object> simularUno(Long partidoId, Random rng) {
        try {
            return simulacion.simularPartido(partidoId, rng);
        } catch (RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("partido_id", partidoId);
            err.put("error", String.valueOf(e.getMessage()));
            return err;
        }
    }

    private Map<String, Object> reentrenar() {
        try {
            ModeloPredictivo m = trainer.entrenarResultado();
            return Map.of("version", m.getVersion(), "accuracy", m.getAccuracy(),
                    "log_loss", m.getLogLoss(), "brier", m.getBrier());
        } catch (Exception e) {
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }

    // El id de un proxy LAZY se obtiene sin inicializarlo; el nombre se resuelve por repositorio
    // (este servicio no abre transacción y open-in-view está deshabilitado).
    private Long ganadorId(Partido p) {
        int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
        int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();
        return gl >= gv ? p.getEquipoLocal().getId() : p.getEquipoVisitante().getId();
    }

    private Long perdedorId(Partido p) {
        int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
        int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();
        return gl >= gv ? p.getEquipoVisitante().getId() : p.getEquipoLocal().getId();
    }

    private String nombre(Long equipoId) {
        return equipoId == null ? null
                : equipoRepo.findById(equipoId).map(dev.solomillo.domain.Equipo::getNombre).orElse(null);
    }
}
