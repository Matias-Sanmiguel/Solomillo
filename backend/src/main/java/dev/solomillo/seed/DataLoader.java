package dev.solomillo.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(1)
public class DataLoader implements ApplicationRunner {

    private final TorneoRepository torneoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public DataLoader(TorneoRepository t, EquipoRepository e, JugadorRepository j,
                      PartidoRepository p, ObjectMapper m, AppProperties props) {
        this.torneoRepo = t; this.equipoRepo = e; this.jugadorRepo = j;
        this.partidoRepo = p; this.mapper = m; this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (props.apiFootballKey != null && !props.apiFootballKey.isBlank()) return;
        JsonNode root = mapper.readTree(new ClassPathResource("seed/mundial.json").getInputStream());
        JsonNode t = root.get("torneo");
        if (torneoRepo.findByNombreAndTemporada(t.get("nombre").asText(), t.get("temporada").asText()).isPresent()) return;

        Torneo torneo = new Torneo();
        torneo.setNombre(t.get("nombre").asText());
        torneo.setCategoria(t.get("categoria").asText());
        torneo.setTemporada(t.get("temporada").asText());
        torneo.setFechaInicio(LocalDate.parse(t.get("fechaInicio").asText()));
        torneo.setFechaFin(LocalDate.parse(t.get("fechaFin").asText()));
        torneoRepo.save(torneo);

        Map<String, Equipo> equipos = new HashMap<>();
        for (JsonNode e : root.get("equipos")) {
            String nombre = e.get("nombre").asText();
            // Reutiliza la seleccion ya sembrada (FifaEloSeedService) en lugar de duplicarla.
            Equipo equipo = equipoRepo.findByNombre(nombre).orElseGet(Equipo::new);
            equipo.setNombre(nombre);
            equipo.setEntrenador(e.get("entrenador").asText());
            equipo.setSede(e.get("sede").asText());
            equipoRepo.save(equipo);
            equipos.put(nombre, equipo);

            // Solo agrega jugadores si el equipo aun no tiene (evita duplicar planteles).
            if (jugadorRepo.findByEquipoId(equipo.getId()).isEmpty()) {
                for (JsonNode j : e.get("jugadores")) {
                    Jugador jug = new Jugador();
                    jug.setEquipo(equipo);
                    jug.setNombre(j.get("nombre").asText());
                    jug.setPosicion(j.get("posicion").asText());
                    jug.setNumeroCamiseta(j.get("numeroCamiseta").asInt());
                    jug.setNacionalidad(j.get("nacionalidad").asText());
                    jug.setFechaNacimiento(LocalDate.parse(j.get("fechaNacimiento").asText()));
                    jugadorRepo.save(jug);
                }
            }
        }

        for (JsonNode p : root.get("partidos")) {
            Partido partido = new Partido();
            partido.setTorneo(torneo);
            partido.setEquipoLocal(equipos.get(p.get("local").asText()));
            partido.setEquipoVisitante(equipos.get(p.get("visitante").asText()));
            partido.setFechaHora(LocalDateTime.parse(p.get("fechaHora").asText()));
            partido.setEstadio(p.get("estadio").asText());
            partidoRepo.save(partido);
        }
    }
}
