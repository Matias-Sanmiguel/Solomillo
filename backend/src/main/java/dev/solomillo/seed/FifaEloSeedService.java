package dev.solomillo.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.NivelTorneo;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.events.EventoDeportivo;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.ml.EloService;
import dev.solomillo.rankings.RankingsService;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.EventoDeportivoRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.TorneoRepository;
import dev.solomillo.stats.CalculadorEstadistica;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seed sin API key: selecciones con puntos FIFA + Elo, e historico simulado
 * de partidos a partir del Elo (resultados coherentes para entrenar el modelo).
 */
@Component
@Order(1)
public class FifaEloSeedService implements ApplicationRunner {

    private static final String TORNEO_ELIM = "Eliminatorias Mundial 2026 (Simulado)";
    private static final String TORNEO_MUNDIAL = "Copa Mundial FIFA 2026";
    private static final String TEMPORADA = "2026";
    private static final double HOME_ADV = 100.0;
    private static final long SEED = 7L;

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;
    private final EloService eloService;
    // Reutilizamos las MISMAS reglas del flujo en vivo (sin Publisher/alertas) para no divergir.
    private final List<CalculadorEstadistica> calculadores;
    private final RankingsService rankings;
    private final EventoDeportivoRepository eventoRepo;

    public FifaEloSeedService(AppProperties props, ObjectMapper mapper,
                              TorneoRepository torneoRepo, EquipoRepository equipoRepo,
                              JugadorRepository jugadorRepo, PartidoRepository partidoRepo,
                              EloService eloService, List<CalculadorEstadistica> calculadores,
                              RankingsService rankings, EventoDeportivoRepository eventoRepo) {
        this.props = props;
        this.mapper = mapper;
        this.torneoRepo = torneoRepo;
        this.equipoRepo = equipoRepo;
        this.jugadorRepo = jugadorRepo;
        this.partidoRepo = partidoRepo;
        this.eloService = eloService;
        this.calculadores = calculadores;
        this.rankings = rankings;
        this.eventoRepo = eventoRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (props.apiFootballKey != null && !props.apiFootballKey.isBlank()) return;
        if (torneoRepo.findByNombreAndTemporada(TORNEO_MUNDIAL, TEMPORADA).isPresent()) return;

        // Eliminatorias (K=40, con localía): aportan el histórico para el Elo y el ML.
        Torneo eliminatorias = new Torneo();
        eliminatorias.setNombre(TORNEO_ELIM);
        eliminatorias.setCategoria("Selecciones");
        eliminatorias.setTemporada(TEMPORADA);
        eliminatorias.setNivel(NivelTorneo.CLASIFICATORIO);
        eliminatorias.setFechaInicio(LocalDate.parse("2025-03-01"));
        eliminatorias.setFechaFin(LocalDate.parse("2026-03-31"));
        torneoRepo.save(eliminatorias);

        // Mundial 2026 (K=60, cancha neutral): partidos próximos a pronosticar.
        Torneo mundial = new Torneo();
        mundial.setNombre(TORNEO_MUNDIAL);
        mundial.setCategoria("Selecciones");
        mundial.setTemporada(TEMPORADA);
        mundial.setNivel(NivelTorneo.MUNDIAL);
        mundial.setFechaInicio(LocalDate.parse("2026-06-11"));
        mundial.setFechaFin(LocalDate.parse("2026-07-19"));
        torneoRepo.save(mundial);

        List<Equipo> equipos = cargarEquipos();
        var rng = new Random(SEED);

        // Histórico de eliminatorias: round-robin simple, fechas pasadas, resultado simulado por Elo.
        List<int[]> pares = new ArrayList<>();
        for (int i = 0; i < equipos.size(); i++)
            for (int j = i + 1; j < equipos.size(); j++) pares.add(new int[]{i, j});
        Collections.shuffle(pares, rng);

        // Cache de jugadores por equipo para atribuir goles/tarjetas al reproducir eventos.
        Map<Long, List<Long>> jugadoresPorEquipo = new HashMap<>();

        LocalDateTime fecha = LocalDateTime.now().minusDays(pares.size() + 20L);
        for (int[] par : pares) {
            Equipo local = rng.nextBoolean() ? equipos.get(par[0]) : equipos.get(par[1]);
            Equipo visit = local == equipos.get(par[0]) ? equipos.get(par[1]) : equipos.get(par[0]);
            Partido p = new Partido();
            p.setTorneo(eliminatorias);
            p.setEquipoLocal(local);
            p.setEquipoVisitante(visit);
            p.setFechaHora(fecha);
            p.setEstadio(local.getNombre());
            p.setNeutral(false);
            int[] goles = simular(local, visit, rng);
            p.setGolesLocal(goles[0]);
            p.setGolesVisitante(goles[1]);
            p.setEstado(EstadoPartido.FINALIZADO);
            partidoRepo.save(p);
            eloService.aplicarResultado(p);
            // Reproduce los eventos del partido por el flujo real -> estadisticas y posiciones.
            reproducirEventos(p, jugadoresPorEquipo, rng);
            fecha = fecha.plusDays(1);
        }

        // Próximos partidos del Mundial (cancha neutral) para el tablero y el prode.
        LocalDateTime futuro = LocalDateTime.now().plusDays(2);
        for (int k = 0; k < 16; k++) {
            int a = rng.nextInt(equipos.size());
            int b = rng.nextInt(equipos.size());
            if (a == b) { b = (b + 1) % equipos.size(); }
            Partido p = new Partido();
            p.setTorneo(mundial);
            p.setEquipoLocal(equipos.get(a));
            p.setEquipoVisitante(equipos.get(b));
            p.setFechaHora(futuro);
            p.setEstadio("Sede Mundial 2026");
            p.setNeutral(true);
            p.setEstado(EstadoPartido.PROGRAMADO);
            partidoRepo.save(p);
            futuro = futuro.plusDays(2);
        }
    }

    private List<Equipo> cargarEquipos() throws Exception {
        JsonNode root = mapper.readTree(new ClassPathResource("seed/selecciones.json").getInputStream());
        List<Equipo> equipos = new ArrayList<>();
        for (JsonNode n : root) {
            String nombre = n.get("nombre").asText();
            int puntos = n.get("puntosFifa").asInt();
            // Prior del rating: rating real de eloratings.net; fallback a puntos FIFA.
            double eloInicial = n.hasNonNull("elo") ? n.get("elo").asDouble() : puntos;
            String escudo = "https://flagcdn.com/w160/" + n.get("codigo").asText() + ".png";
            Equipo e = equipoRepo.findByNombre(nombre).orElseGet(Equipo::new);
            e.setNombre(nombre);
            e.setPuntosFifa(puntos);
            e.setElo(eloInicial);
            e.setEscudo(escudo);
            if (e.getSede() == null) e.setSede("");
            equipoRepo.save(e);
            equipos.add(e);

            if (jugadorRepo.findByEquipoId(e.getId()).isEmpty()) {
                String[] pos = {"Delantero", "Mediocampista", "Defensor"};
                for (int k = 0; k < pos.length; k++) {
                    Jugador j = new Jugador();
                    j.setEquipo(e);
                    j.setNombre(nombre + " " + pos[k]);
                    j.setPosicion(pos[k]);
                    j.setNumeroCamiseta(k + 9);
                    jugadorRepo.save(j);
                }
            }
        }
        return equipos;
    }

    private int[] simular(Equipo local, Equipo visit, Random rng) {
        double eLocal = local.getElo() != null ? local.getElo() : EloService.BASE;
        double eVisit = visit.getElo() != null ? visit.getElo() : EloService.BASE;
        double expHome = 1.0 / (1.0 + Math.pow(10, (eVisit - eLocal - HOME_ADV) / 400.0));
        double muLocal = Math.max(0.2, 1.35 + 1.6 * (expHome - 0.5));
        double muVisit = Math.max(0.2, 1.35 - 1.6 * (expHome - 0.5));
        return new int[]{poisson(rng, muLocal), poisson(rng, muVisit)};
    }

    private int poisson(Random rng, double mu) {
        double l = Math.exp(-mu);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return Math.min(k - 1, 7);
    }

    /** Reproduce los goles (atribuidos a jugadores), unas tarjetas y el fin del partido. */
    private void reproducirEventos(Partido p, Map<Long, List<Long>> cache, Random rng) {
        List<Long> jl = cache.computeIfAbsent(p.getEquipoLocal().getId(), this::jugadoresDe);
        List<Long> jv = cache.computeIfAbsent(p.getEquipoVisitante().getId(), this::jugadoresDe);

        int minuto = 0;
        for (int g = 0; g < p.getGolesLocal() && !jl.isEmpty(); g++) {
            minuto = Math.min(90, minuto + 7);
            procesar(new EventoInterno("gol", p.getId(), minuto, jl.get(g % jl.size()), Map.of()));
        }
        for (int g = 0; g < p.getGolesVisitante() && !jv.isEmpty(); g++) {
            minuto = Math.min(90, minuto + 7);
            procesar(new EventoInterno("gol", p.getId(), minuto, jv.get(g % jv.size()), Map.of()));
        }

        // Algunas tarjetas (mayoría amarillas, alguna roja) para que el comparador
        // tenga métricas de disciplina diferenciadas (amarillas vs rojas).
        for (int t = 0, n = rng.nextInt(4); t < n; t++) {
            boolean local = rng.nextBoolean();
            List<Long> js = local ? jl : jv;
            if (js.isEmpty()) continue;
            Long jug = js.get(rng.nextInt(js.size()));
            // ~15% rojas: poco frecuentes pero suficientes para poblar la métrica.
            String color = rng.nextDouble() < 0.15 ? "RED_CARD" : "YELLOW_CARD";
            procesar(new EventoInterno("tarjeta", p.getId(), 10 + rng.nextInt(80), jug,
                    Map.of("eventType", color)));
        }

        procesar(new EventoInterno("fin_partido", p.getId(), 90, null, Map.of()));
    }

    /**
     * Mismo pipeline que {@code MotorProcesamiento.procesar} pero sin Publisher ni alertas:
     * persiste el evento y aplica los calculadores de estadistica y el ranking reales.
     */
    private void procesar(EventoInterno e) {
        var ed = new EventoDeportivo();
        ed.setPartidoId(e.partidoId());
        ed.setJugadorId(e.jugadorId());
        ed.setTipo(e.tipo());
        ed.setMinuto(e.minuto());
        ed.setFuente("seed");
        eventoRepo.save(ed);

        calculadores.stream().filter(c -> c.aplica(e)).forEach(c -> c.actualizar(e));
        rankings.actualizar(e);
    }

    private List<Long> jugadoresDe(Long equipoId) {
        return jugadorRepo.findByEquipoId(equipoId).stream().map(j -> j.getId()).toList();
    }
}
