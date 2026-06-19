package dev.solomillo.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.core.AppProperties;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Jugador;
import dev.solomillo.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Enriquece las selecciones del Mundial 2026 (sembradas por {@link FifaEloSeedService})
 * con datos reales: director técnico y jugadores estrella. No crea torneos ni partidos
 * propios — el calendario del Mundial 2026 lo aporta {@code FifaEloSeedService}.
 */
@Component
@Order(1)
public class DataLoader implements ApplicationRunner {

    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public DataLoader(EquipoRepository e, JugadorRepository j, ObjectMapper m, AppProperties props) {
        this.equipoRepo = e; this.jugadorRepo = j; this.mapper = m; this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (props.apiFootballKey != null && !props.apiFootballKey.isBlank()) return;
        JsonNode root = mapper.readTree(new ClassPathResource("seed/mundial.json").getInputStream());

        for (JsonNode e : root.get("equipos")) {
            String nombre = e.get("nombre").asText();
            // Reutiliza la seleccion ya sembrada (FifaEloSeedService) en lugar de duplicarla.
            Equipo equipo = equipoRepo.findByNombre(nombre).orElseGet(Equipo::new);
            equipo.setNombre(nombre);
            equipo.setEntrenador(e.get("entrenador").asText());
            equipo.setSede(e.get("sede").asText());
            equipoRepo.save(equipo);

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
    }
}
