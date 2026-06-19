package dev.solomillo.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.domain.Equipo;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.EstadisticaEquipoRepository;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PosicionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que tras el seed (sin API key) el sistema arranca con estadisticas y posiciones
 * ya calculadas a partir de los partidos finalizados, y que el comparador devuelve datos
 * reales por los endpoints HTTP que usa el frontend.
 *
 * Requiere Postgres y Redis (docker compose up -d db redis) sobre una base recien sembrada.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5432/solomillo",
                "spring.data.redis.url=redis://localhost:6379",
                "app.models-dir=target/models-test"
        })
class SeedStatsVerificationTest {

    @Autowired TestRestTemplate http;
    @Autowired EquipoRepository equipoRepo;
    @Autowired JugadorRepository jugadorRepo;
    @Autowired EstadisticaEquipoRepository eeRepo;
    @Autowired EstadisticaJugadorRepository ejRepo;
    @Autowired PosicionRepository posicionRepo;
    @Autowired ObjectMapper mapper;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void seedPueblaEstadisticasYComparadorDevuelveDatos() throws Exception {
        long equipos = equipoRepo.count();
        long jugadores = jugadorRepo.count();
        long statsEquipo = eeRepo.count();
        long statsJugador = ejRepo.count();
        long posiciones = posicionRepo.count();

        System.out.println("\n=== CONTEO POST-SEED ===");
        System.out.println("equipos=" + equipos + " jugadores=" + jugadores
                + " estadisticas_equipo=" + statsEquipo + " estadisticas_jugador=" + statsJugador
                + " posiciones=" + posiciones);

        // Sin duplicados: las 24 selecciones de selecciones.json.
        assertEquals(24, equipos, "no debe haber equipos duplicados (Argentina, Francia, etc.)");
        assertTrue(statsEquipo > 0, "el seed debe dejar estadisticas de equipo");
        assertTrue(statsJugador > 0, "el seed debe dejar estadisticas de jugador");
        assertTrue(posiciones > 0, "el seed debe dejar posiciones");

        // --- Prueba del comparador por HTTP para dos selecciones del Mundial 2026 -------------
        Equipo a = equipoRepo.findByNombre("España").orElseThrow();
        Equipo b = equipoRepo.findByNombre("Brasil").orElseThrow();

        List<Map<String, Object>> statsA = http.getForObject("/equipos/" + a.getId() + "/estadisticas", List.class);
        List<Map<String, Object>> statsB = http.getForObject("/equipos/" + b.getId() + "/estadisticas", List.class);

        System.out.println("\n=== GET /equipos/" + a.getId() + "/estadisticas (España) ===");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(statsA));
        System.out.println("=== GET /equipos/" + b.getId() + "/estadisticas (Brasil) ===");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(statsB));

        assertNotNull(statsA);
        assertNotNull(statsB);
        assertFalse(statsA.isEmpty(), "España debe tener estadisticas reales");
        assertFalse(statsB.isEmpty(), "Brasil debe tener estadisticas reales");
        assertTrue(statsA.stream().anyMatch(s -> "goles".equals(s.get("metrica"))),
                "debe existir la metrica goles para España");
    }
}
