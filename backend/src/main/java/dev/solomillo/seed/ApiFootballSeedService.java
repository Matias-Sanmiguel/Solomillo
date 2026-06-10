package dev.solomillo.seed;

import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.ingest.ApiFootballClient;
import dev.solomillo.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(2)
public class ApiFootballSeedService implements ApplicationRunner {

    private static final int WORLD_CUP_LEAGUE_ID = 1;

    private final AppProperties props;
    private final ApiFootballClient client;
    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;

    public ApiFootballSeedService(AppProperties props, ApiFootballClient client,
                                   TorneoRepository torneoRepo, EquipoRepository equipoRepo,
                                   JugadorRepository jugadorRepo, PartidoRepository partidoRepo) {
        this.props = props;
        this.client = client;
        this.torneoRepo = torneoRepo;
        this.equipoRepo = equipoRepo;
        this.jugadorRepo = jugadorRepo;
        this.partidoRepo = partidoRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.apiFootballKey == null || props.apiFootballKey.isBlank()) return;
        seedMundial(2022, "2022-11-20", "2022-12-18");
        seedMundial(2026, "2026-06-11", "2026-07-19");
    }

    @Transactional
    public void seedMundial(int season, String fechaInicio, String fechaFin) {
        String temporada = String.valueOf(season);
        if (torneoRepo.findByNombreAndTemporada("Copa Mundial FIFA", temporada).isPresent()) return;

        Torneo torneo = new Torneo();
        torneo.setNombre("Copa Mundial FIFA");
        torneo.setCategoria("Selecciones");
        torneo.setTemporada(temporada);
        torneo.setFechaInicio(LocalDate.parse(fechaInicio));
        torneo.setFechaFin(LocalDate.parse(fechaFin));
        torneoRepo.save(torneo);

        Map<Integer, Equipo> equiposByApiId = fetchAndSaveEquipos(season);
        fetchAndSaveFixtures(torneo, season, equiposByApiId);
        fetchAndSavePlayers(season, equiposByApiId);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Equipo> fetchAndSaveEquipos(int season) {
        Map<Integer, Equipo> byApiId = new HashMap<>();
        List<Map<String, Object>> response = client.equiposLiga(WORLD_CUP_LEAGUE_ID, season);

        for (Map<String, Object> item : response) {
            Map<String, Object> teamData = (Map<String, Object>) item.get("team");
            Map<String, Object> venueData = (Map<String, Object>) item.getOrDefault("venue", Map.of());

            String nombre = (String) teamData.get("name");
            Equipo equipo = equipoRepo.findByNombre(nombre).orElseGet(Equipo::new);
            equipo.setNombre(nombre);
            equipo.setEscudo((String) teamData.get("logo"));
            equipo.setSede((String) venueData.getOrDefault("city", ""));
            equipo.setEstadio((String) venueData.getOrDefault("name", ""));
            equipoRepo.save(equipo);

            byApiId.put(((Number) teamData.get("id")).intValue(), equipo);
        }
        return byApiId;
    }

    @SuppressWarnings("unchecked")
    private void fetchAndSaveFixtures(Torneo torneo, int season, Map<Integer, Equipo> equiposByApiId) {
        List<Map<String, Object>> response = client.fixtures(WORLD_CUP_LEAGUE_ID, season);

        for (Map<String, Object> item : response) {
            Map<String, Object> fixtureData = (Map<String, Object>) item.get("fixture");
            Map<String, Object> teamsData = (Map<String, Object>) item.get("teams");
            Map<String, Object> home = (Map<String, Object>) teamsData.get("home");
            Map<String, Object> away = (Map<String, Object>) teamsData.get("away");
            Map<String, Object> venue = (Map<String, Object>) fixtureData.getOrDefault("venue", Map.of());

            Equipo local = equiposByApiId.get(((Number) home.get("id")).intValue());
            Equipo visitante = equiposByApiId.get(((Number) away.get("id")).intValue());
            if (local == null || visitante == null) continue;

            Partido partido = new Partido();
            partido.setTorneo(torneo);
            partido.setEquipoLocal(local);
            partido.setEquipoVisitante(visitante);
            partido.setEstadio((String) venue.getOrDefault("name", ""));

            String dateStr = (String) fixtureData.get("date");
            if (dateStr != null) partido.setFechaHora(OffsetDateTime.parse(dateStr).toLocalDateTime());

            partidoRepo.save(partido);
        }
    }

    @SuppressWarnings("unchecked")
    private void fetchAndSavePlayers(int season, Map<Integer, Equipo> equiposByApiId) {
        for (Map.Entry<Integer, Equipo> entry : equiposByApiId.entrySet()) {
            List<Map<String, Object>> response = client.jugadoresEquipo(entry.getKey(), season);

            for (Map<String, Object> item : response) {
                Map<String, Object> playerData = (Map<String, Object>) item.get("player");
                List<Map<String, Object>> stats = (List<Map<String, Object>>) item.getOrDefault("statistics", List.of());

                Jugador jug = new Jugador();
                jug.setEquipo(entry.getValue());
                jug.setNombre((String) playerData.get("name"));
                jug.setNacionalidad((String) playerData.getOrDefault("nationality", ""));

                Map<String, Object> birth = (Map<String, Object>) playerData.getOrDefault("birth", Map.of());
                String birthDate = (String) birth.get("date");
                if (birthDate != null && !birthDate.isBlank()) {
                    jug.setFechaNacimiento(LocalDate.parse(birthDate));
                }

                if (!stats.isEmpty()) {
                    Map<String, Object> games = (Map<String, Object>) stats.get(0).getOrDefault("games", Map.of());
                    jug.setPosicion(mapPosicion((String) games.getOrDefault("position", "")));
                    Object num = games.get("number");
                    if (num != null) jug.setNumeroCamiseta(((Number) num).intValue());
                }

                jugadorRepo.save(jug);
            }
        }
    }

    private String mapPosicion(String pos) {
        if (pos == null) return "";
        return switch (pos) {
            case "Goalkeeper" -> "Arquero";
            case "Defender" -> "Defensor";
            case "Midfielder" -> "Mediocampista";
            case "Attacker" -> "Delantero";
            default -> pos;
        };
    }
}
