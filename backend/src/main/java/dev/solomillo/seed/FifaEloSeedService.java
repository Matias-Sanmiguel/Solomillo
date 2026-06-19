package dev.solomillo.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.NivelTorneo;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Ronda;
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
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.stats.EstadisticaJugador;

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
@Order(2) // DataLoader (@Order 1) debe correr primero: crea los equipos del Mundial 2022 con su
          // plantel real; este seed luego los reusa por nombre (findByNombre) y evita duplicados.
public class FifaEloSeedService implements ApplicationRunner {

    private static final String TORNEO_ELIM = "Eliminatorias Mundial 2026 (Simulado)";
    private static final String TORNEO_MUNDIAL = "Copa Mundial FIFA 2026";
    private static final String TEMPORADA = "2026";
    private static final double HOME_ADV = 100.0;
    private static final long SEED = 7L;
    // Tope de cruces simulados de eliminatorias (historial para Elo/ML sin explotar con 48 selecciones).
    private static final int MAX_ELIMINATORIAS = 240;

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;
    private final EloService eloService;
    private final List<CalculadorEstadistica> calculadores;
    private final RankingsService rankings;
    private final EventoDeportivoRepository eventoRepo;
    private final dev.solomillo.repository.EstadisticaJugadorRepository ejRepo;

    public FifaEloSeedService(AppProperties props, ObjectMapper mapper,
                              TorneoRepository torneoRepo, EquipoRepository equipoRepo,
                              JugadorRepository jugadorRepo, PartidoRepository partidoRepo,
                              EloService eloService, List<CalculadorEstadistica> calculadores,
                              RankingsService rankings, EventoDeportivoRepository eventoRepo, EstadisticaJugadorRepository ejRepo) {
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
        this.ejRepo = ejRepo;
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

        // Histórico de eliminatorias: muestra de cruces (no round-robin completo, que con 48
        // selecciones serían >1100 partidos), fechas pasadas, resultado simulado por Elo.
        List<int[]> pares = new ArrayList<>();
        for (int i = 0; i < equipos.size(); i++)
            for (int j = i + 1; j < equipos.size(); j++) pares.add(new int[]{i, j});
        Collections.shuffle(pares, rng);
        if (pares.size() > MAX_ELIMINATORIAS) pares = pares.subList(0, MAX_ELIMINATORIAS);

        // Cache de jugadores por equipo para atribuir goles/tarjetas al reproducir eventos.
        Map<Long, List<Jugador>> jugadoresPorEquipo = new HashMap<>();

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
            reproducirEventos(p, jugadoresPorEquipo, rng);
            fecha = fecha.plusDays(1);
        }

        // Fixture REAL del Mundial 2026 (grupos con resultados ya jugados + programados + llave).
        cargarFixtureMundial(mundial, equipos, jugadoresPorEquipo, rng);
    }

    /**
     * Carga el calendario real del Mundial 2026 desde {@code mundial2026.json}. Los partidos
     * FINALIZADOS (y el EN_VIVO con marcador) reproducen sus eventos por el flujo real para
     * poblar estadísticas y posiciones; los de llave aún sin rival van con equipos null (TBD).
     */
    private void cargarFixtureMundial(Torneo mundial, List<Equipo> equipos,
                                      Map<Long, List<Jugador>> cache, Random rng) throws Exception {
        Map<String, Equipo> porNombre = new HashMap<>();
        for (Equipo e : equipos) porNombre.put(e.getNombre(), e);

        JsonNode root = mapper.readTree(new ClassPathResource("seed/mundial2026.json").getInputStream());
        for (JsonNode n : root.get("partidos")) {
            Partido p = new Partido();
            p.setTorneo(mundial);
            if (n.hasNonNull("local")) p.setEquipoLocal(porNombre.get(n.get("local").asText()));
            if (n.hasNonNull("visitante")) p.setEquipoVisitante(porNombre.get(n.get("visitante").asText()));
            if (n.hasNonNull("grupo")) p.setGrupo(n.get("grupo").asText());
            p.setRonda(Ronda.valueOf(n.get("ronda").asText()));
            p.setFechaHora(LocalDateTime.parse(n.get("fecha").asText()));
            p.setEstadio(n.get("estadio").asText());
            p.setNeutral(true);

            String estado = n.get("estado").asText();
            boolean tieneMarcador = n.has("golesLocal") && n.has("golesVisitante");
            if (tieneMarcador) {
                p.setGolesLocal(n.get("golesLocal").asInt());
                p.setGolesVisitante(n.get("golesVisitante").asInt());
            }
            p.setEstado(EstadoPartido.valueOf(estado));
            partidoRepo.save(p);

            // ELO solo para partidos cerrados; estadísticas/posiciones también para el EN_VIVO
            // (el marcador actual ya cuenta en la tabla, como en las capturas).
            if ("FINALIZADO".equals(estado)) {
                eloService.aplicarResultado(p);
                reproducirEventos(p, cache, rng);
            } else if ("EN_VIVO".equals(estado) && tieneMarcador) {
                reproducirEventos(p, cache, rng);
            }
        }
    }

    private List<Equipo> cargarEquipos() throws Exception {
        JsonNode root = mapper.readTree(new ClassPathResource("seed/selecciones.json").getInputStream());
        List<Equipo> equipos = new ArrayList<>();
        for (JsonNode n : root) {
            String nombre = n.get("nombre").asText();
            int puntos = n.get("puntosFifa").asInt();
            double eloInicial = n.hasNonNull("elo") ? n.get("elo").asDouble() : puntos;
            String escudo = "https://flagcdn.com/w160/" + n.get("codigo").asText() + ".png";
            Equipo e = equipoRepo.findByNombre(nombre).orElseGet(Equipo::new);
            e.setNombre(nombre);
            e.setPuntosFifa(puntos);
            e.setElo(eloInicial);
            e.setEscudo(escudo);
            if (n.hasNonNull("grupo")) e.setGrupo(n.get("grupo").asText());
            if (e.getSede() == null) e.setSede("");
            equipoRepo.save(e);
            equipos.add(e);
            // No generamos jugadores genéricos: las selecciones que jugaron el Mundial ya traen su
            // plantel real desde mundial.json (DataLoader). Las que solo están en el ranking FIFA y no
            // disputaron el Mundial (p. ej. Colombia, Italia) quedan sin jugadores: siguen en el Elo/FIFA
            // y en las eliminatorias simuladas, pero no aportan goleadores (atribuirGoles las saltea).
        }
        return equipos;
    }

    /** Reparte los goles simulados del partido entre los jugadores del equipo,
     *  ponderando por posición, y los acumula en estadisticas_jugador (metrica "goles"). */
    private void atribuirGoles(Equipo equipo, Long torneoId, int goles, Random rng) {
        if (goles <= 0) return;
        List<Jugador> plantel = jugadorRepo.findByEquipoId(equipo.getId());
        if (plantel.isEmpty()) return;
        for (int g = 0; g < goles; g++) {
            Jugador autor = elegirGoleador(plantel, rng);
            var stat = ejRepo.findByJugadorIdAndTorneoIdAndMetrica(autor.getId(), torneoId, "goles")
                    .orElseGet(() -> {
                        var s = new EstadisticaJugador();
                        s.setJugadorId(autor.getId());
                        s.setTorneoId(torneoId);
                        s.setMetrica("goles");
                        return s;
                    });
            stat.setValor(stat.getValor() + 1);
            ejRepo.save(stat);
        }
    }

    private Jugador elegirGoleador(List<Jugador> plantel, Random rng) {
        double total = 0;
        for (Jugador j : plantel) total += pesoPosicion(j.getPosicion());
        double r = rng.nextDouble() * total;
        for (Jugador j : plantel) {
            r -= pesoPosicion(j.getPosicion());
            if (r <= 0) return j;
        }
        return plantel.get(plantel.size() - 1);
    }

    private double pesoPosicion(String posicion) {
        if (posicion == null) return 1.0;
        return switch (posicion) {
            case "Delantero" -> 6.0;
            case "Mediocampista" -> 3.0;
            case "Defensor" -> 1.0;
            default -> 1.0;
        };
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
    private void reproducirEventos(Partido p, Map<Long, List<Jugador>> cache, Random rng) {
        List<Jugador> jl = cache.computeIfAbsent(p.getEquipoLocal().getId(), this::jugadoresDe);
        List<Jugador> jv = cache.computeIfAbsent(p.getEquipoVisitante().getId(), this::jugadoresDe);

        Long torneoId = p.getTorneo().getId();
        int minuto = 0;
        // El goleador se elige ponderando por posición (Delantero > Mediocampista > Defensor),
        // no por orden de plantel: si no, el arquero (dorsal 1) lideraba la tabla de goleadores.
        for (int g = 0; g < p.getGolesLocal() && !jl.isEmpty(); g++) {
            minuto = Math.min(90, minuto + 7);
            procesar(new EventoInterno("gol", p.getId(), minuto, elegirGoleador(jl, rng).getId(), Map.of()));
        }
        for (int g = 0; g < p.getGolesVisitante() && !jv.isEmpty(); g++) {
            minuto = Math.min(90, minuto + 7);
            procesar(new EventoInterno("gol", p.getId(), minuto, elegirGoleador(jv, rng).getId(), Map.of()));
        }

        // Tarjetas: amarillas mayoritariamente, rojas esporádicas.
        for (int t = 0, n = rng.nextInt(5); t < n; t++) {
            boolean esLocal = rng.nextBoolean();
            List<Jugador> js = esLocal ? jl : jv;
            if (js.isEmpty()) continue;
            Long jug = js.get(rng.nextInt(js.size())).getId();
            String tipo = rng.nextInt(10) < 2 ? "RED_CARD" : "YELLOW_CARD";
            procesar(new EventoInterno("tarjeta", p.getId(), 10 + rng.nextInt(80), jug,
                    Map.of("eventType", tipo)));
        }

        procesar(new EventoInterno("fin_partido", p.getId(), 90, null, Map.of()));
    }

    /** ~65% de los goles tienen asistencia, acreditada a un compañero distinto al goleador. */
    private void asistencia(List<Long> equipo, Long goleador, Long torneoId, Random rng) {
        if (equipo.size() < 2 || rng.nextDouble() >= 0.65) return;
        Long asistente;
        do { asistente = equipo.get(rng.nextInt(equipo.size())); } while (asistente.equals(goleador));
        final Long jId = asistente;
        var stat = ejRepo.findByJugadorIdAndTorneoIdAndMetrica(jId, torneoId, "asistencias")
                .orElseGet(() -> {
                    var s = new dev.solomillo.stats.EstadisticaJugador();
                    s.setJugadorId(jId); s.setTorneoId(torneoId); s.setMetrica("asistencias");
                    return s;
                });
        stat.setValor(stat.getValor() + 1);
        ejRepo.save(stat);
    }

    /**
     * Mismo pipeline que MotorProcesamiento.procesar pero sin Publisher ni alertas:
     * persiste el evento y aplica los calculadores de estadística y el ranking reales.
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

    private List<Jugador> jugadoresDe(Long equipoId) {
        return jugadorRepo.findByEquipoId(equipoId);
    }
}
