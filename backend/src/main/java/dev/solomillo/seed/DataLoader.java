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

/**
 * Carga los planteles REALES del Mundial 2026 (PDF de convocatorias FIFA) desde
 * {@code planteles2026.json}: por selección, director técnico y los 26 jugadores
 * (nombre, posición, dorsal, club). Corre primero (@Order 1); {@link FifaEloSeedService}
 * (@Order 2) reutiliza estos equipos/jugadores por nombre y no genera planteles genéricos.
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
        JsonNode root = mapper.readTree(new ClassPathResource("seed/planteles2026.json").getInputStream());

        for (JsonNode e : root.get("equipos")) {
            String nombre = e.get("nombre").asText();
            // Reutiliza la seleccion ya sembrada (si existe) en lugar de duplicarla.
            Equipo equipo = equipoRepo.findByNombre(nombre).orElseGet(Equipo::new);
            equipo.setNombre(nombre);
            if (e.hasNonNull("dt")) equipo.setEntrenador(e.get("dt").asText());
            equipoRepo.save(equipo);

            // Solo agrega jugadores si el equipo aun no tiene (evita duplicar planteles).
            if (jugadorRepo.findByEquipoId(equipo.getId()).isEmpty()) {
                for (JsonNode j : e.get("jugadores")) {
                    Jugador jug = new Jugador();
                    jug.setEquipo(equipo);
                    String nom = j.path("nombre").asText("").trim();
                    String ape = j.path("apellido").asText("").trim();
                    jug.setNombre((nom + " " + ape).trim());
                    jug.setPosicion(j.path("posicion").asText(""));
                    if (j.hasNonNull("numero")) jug.setNumeroCamiseta(j.get("numero").asInt());
                    if (j.hasNonNull("club")) jug.setClub(j.get("club").asText());
                    jug.setNacionalidad(nombre);
                    jugadorRepo.save(jug);
                }
            }
        }
    }
}
