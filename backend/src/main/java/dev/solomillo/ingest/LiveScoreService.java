package dev.solomillo.ingest;

import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.events.MotorProcesamiento;
import dev.solomillo.repository.PartidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveScoreService {

    private static final Logger log = LoggerFactory.getLogger(LiveScoreService.class);

    private final FootballDataClient client;
    private final PartidoRepository partidoRepo;
    private final MotorProcesamiento motor;
    private final boolean enabled;

    // matchApiId → last known [golesHome, golesAway]
    private final Map<Integer, int[]> lastScore = new ConcurrentHashMap<>();

    public LiveScoreService(FootballDataClient client, PartidoRepository partidoRepo,
                            MotorProcesamiento motor, AppProperties props) {
        this.client = client;
        this.partidoRepo = partidoRepo;
        this.motor = motor;
        this.enabled = props.footballDataKey != null && !props.footballDataKey.isBlank();
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void tick() {
        if (!enabled) return;

        List<Partido> enVivo = partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.EN_VIVO);
        List<Partido> programados = partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.PROGRAMADO);

        LocalDateTime now = LocalDateTime.now();
        boolean hayInminentes = programados.stream()
                .anyMatch(p -> p.getFechaHora() != null
                        && p.getFechaHora().isAfter(now.minusMinutes(10))
                        && p.getFechaHora().isBefore(now.plusHours(3)));

        if (enVivo.isEmpty() && !hayInminentes) {
            log.debug("LiveScore: no active or imminent matches, skipping");
            return;
        }

        try {
            procesarTick(enVivo, programados);
        } catch (Exception e) {
            log.warn("LiveScore poll error: {}", e.getMessage());
        }
    }

    private void procesarTick(List<Partido> enVivo, List<Partido> programados) {
        List<Map<String, Object>> live = client.matchesLive();
        List<Map<String, Object>> paused = client.matchesPaused();
        List<Map<String, Object>> hoy = client.matchesByDate(LocalDate.now().toString());

        Map<Integer, Map<String, Object>> all = new LinkedHashMap<>();
        for (var m : hoy) all.put(matchId(m), m);
        for (var m : paused) all.put(matchId(m), m);
        for (var m : live) all.put(matchId(m), m);

        Map<String, Partido> porEquipos = buildEquiposMap(enVivo, programados);

        for (var match : all.values()) {
            try {
                procesarUno(match, porEquipos);
            } catch (Exception e) {
                log.debug("LiveScore match {} error: {}", matchId(match), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void procesarUno(Map<String, Object> match, Map<String, Partido> porEquipos) {
        String status = (String) match.get("status");
        var homeTeam = (Map<String, Object>) match.get("homeTeam");
        var awayTeam = (Map<String, Object>) match.get("awayTeam");
        var score = (Map<String, Object>) match.get("score");
        if (homeTeam == null || awayTeam == null || score == null) return;

        int mid = matchId(match);
        String homeName = (String) homeTeam.get("name");
        String awayName = (String) awayTeam.get("name");

        var fullTime = (Map<String, Object>) score.get("fullTime");
        int golesHome = fullTime != null && fullTime.get("home") instanceof Number n ? n.intValue() : 0;
        int golesAway = fullTime != null && fullTime.get("away") instanceof Number n ? n.intValue() : 0;
        int minute = parseMinute(match.get("minute"));

        Partido p = porEquipos.get(normalizar(homeName) + "|" + normalizar(awayName));
        if (p == null) return;

        boolean live = isLive(status);
        boolean finished = isFinished(status);

        if (live && p.getEstado() == EstadoPartido.PROGRAMADO) {
            p.setEstado(EstadoPartido.EN_VIVO);
            p.setGolesLocal(golesHome);
            p.setGolesVisitante(golesAway);
            partidoRepo.save(p);
            lastScore.put(mid, new int[]{golesHome, golesAway});
            motor.procesar(new EventoInterno("inicio_partido", p.getId(), minute, null, Map.of()), "live");
            log.info("LiveScore: {} ({} vs {}) → EN_VIVO", p.getId(), homeName, awayName);
            return;
        }

        if (live && p.getEstado() == EstadoPartido.EN_VIVO) {
            int prevHome = p.getGolesLocal() != null ? p.getGolesLocal() : 0;
            int prevAway = p.getGolesVisitante() != null ? p.getGolesVisitante() : 0;
            int[] prev = lastScore.getOrDefault(mid, new int[]{prevHome, prevAway});

            int newHome = golesHome - prev[0];
            int newAway = golesAway - prev[1];

            for (int i = 0; i < newHome; i++)
                motor.procesar(new EventoInterno("gol", p.getId(), minute, null, Map.of("equipo", "local")), "live");
            for (int i = 0; i < newAway; i++)
                motor.procesar(new EventoInterno("gol", p.getId(), minute, null, Map.of("equipo", "visitante")), "live");

            if (newHome > 0 || newAway > 0) {
                p.setGolesLocal(golesHome);
                p.setGolesVisitante(golesAway);
                partidoRepo.save(p);
                lastScore.put(mid, new int[]{golesHome, golesAway});
            }
        }

        if (finished && p.getEstado() == EstadoPartido.EN_VIVO) {
            p.setEstado(EstadoPartido.FINALIZADO);
            p.setGolesLocal(golesHome);
            p.setGolesVisitante(golesAway);
            partidoRepo.save(p);
            lastScore.remove(mid);
            motor.procesar(new EventoInterno("fin_partido", p.getId(), 90, null, Map.of()), "live");
            log.info("LiveScore: {} → FINALIZADO ({}-{})", p.getId(), golesHome, golesAway);
        }
    }

    private Map<String, Partido> buildEquiposMap(List<Partido> enVivo, List<Partido> programados) {
        Map<String, Partido> map = new HashMap<>();
        for (var p : enVivo)
            map.put(normalizar(p.getEquipoLocal().getNombre()) + "|" + normalizar(p.getEquipoVisitante().getNombre()), p);
        for (var p : programados)
            map.put(normalizar(p.getEquipoLocal().getNombre()) + "|" + normalizar(p.getEquipoVisitante().getNombre()), p);
        return map;
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[áàäâ]", "a").replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i").replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u").replaceAll("[ñ]", "n")
                .trim();
    }

    private boolean isLive(String s) {
        return "IN_PLAY".equals(s) || "PAUSED".equals(s);
    }

    private boolean isFinished(String s) {
        return "FINISHED".equals(s) || "AWARDED".equals(s);
    }

    private int parseMinute(Object m) {
        if (m == null) return 0;
        try { return Integer.parseInt(m.toString().split("\\+")[0].trim()); }
        catch (Exception e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private int matchId(Map<String, Object> match) {
        return match.get("id") instanceof Number n ? n.intValue() : -1;
    }
}
