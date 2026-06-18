package dev.solomillo.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.NivelTorneo;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.rankings.Posicion;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PosicionRepository;
import dev.solomillo.repository.TorneoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verificacion en runtime de la Opcion 1: {@code MlAnalytics.proyectar()} debe funcionar con el
 * modelo de 9 features (mismo header que entrena/predice), sin errores de Weka ni de dimensiones,
 * sobre un torneo con posiciones cargadas y partidos pendientes.
 *
 * Requiere Postgres y Redis (docker compose up -d db redis). Apunta a localhost.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/solomillo",
        "spring.data.redis.url=redis://localhost:6379",
        "app.models-dir=target/models-test"
})
class MlAnalyticsProyectarTest {

    @Autowired MlTrainer trainer;
    @Autowired MlAnalytics analytics;
    @Autowired EquipoRepository equipoRepo;
    @Autowired TorneoRepository torneoRepo;
    @Autowired PartidoRepository partidoRepo;
    @Autowired PosicionRepository posicionRepo;
    @Autowired ObjectMapper mapper;

    @Test
    void proyectarUsaLas9FeaturesYReordenaPorPartidosPendientes() throws Exception {
        // El seed (sin API key) deja >250 partidos finalizados: suficiente para entrenar.
        ModeloPredictivo modelo = trainer.entrenarResultado();
        assertTrue(modelo.isActivo(), "el modelo entrenado debe quedar activo");

        // --- Escenario controlado: torneo con posiciones + pendientes -------------------------
        // Equipos exclusivos de selecciones.json (evita duplicados de nombre con mundial.json).
        Equipo espana = equipo("España");
        Equipo brasil = equipo("Brasil");
        Equipo uruguay = equipo("Uruguay");
        List<Equipo> rivales = List.of(
                equipo("Polonia"), equipo("Corea del Sur"), equipo("Estados Unidos"),
                equipo("Australia"), equipo("Senegal"));

        Torneo torneo = new Torneo();
        torneo.setNombre("PROYECCION_TEST_" + System.currentTimeMillis());
        torneo.setCategoria("Selecciones");
        torneo.setTemporada("2026");
        torneo.setNivel(NivelTorneo.MUNDIAL);
        torneo.setFechaInicio(LocalDate.now());
        torneo.setFechaFin(LocalDate.now().plusDays(30));
        torneoRepo.save(torneo);

        // Tabla actual: Brasil 3 (lider por puntos), España 2, Uruguay 1 (ultimo).
        Posicion pEspana = posicion(torneo.getId(), espana.getId(), 2);
        Posicion pBrasil = posicion(torneo.getId(), brasil.getId(), 3);
        Posicion pUruguay = posicion(torneo.getId(), uruguay.getId(), 1);

        // Uruguay tiene 5 partidos pendientes (como local) contra rivales mas debiles.
        List<Partido> pendientes = new ArrayList<>();
        LocalDateTime cuando = LocalDateTime.now().plusDays(1);
        for (Equipo rival : rivales) {
            Partido p = new Partido();
            p.setTorneo(torneo);
            p.setEquipoLocal(uruguay);
            p.setEquipoVisitante(rival);
            p.setFechaHora(cuando);
            p.setEstadio("Test");
            p.setEstado(EstadoPartido.PROGRAMADO);
            pendientes.add(partidoRepo.save(p));
            cuando = cuando.plusDays(2);
        }

        try {
            // --- Ejecucion real ---------------------------------------------------------------
            List<Map<String, Object>> proyeccion = analytics.proyectar(torneo.getId());

            System.out.println("\n=== JSON proyectar(torneoId=" + torneo.getId() + ") ===");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proyeccion));

            // (1) No hubo error de Weka/dimensiones: si lo hubiera, ya habria lanzado excepcion.
            // (2) Devuelve resultados para el torneo con pendientes (las 3 filas con posicion).
            assertEquals(3, proyeccion.size(), "deben proyectarse los 3 equipos con posicion");

            Map<String, Object> filaUruguay = fila(proyeccion, uruguay.getId());
            Map<String, Object> filaBrasil = fila(proyeccion, brasil.getId());
            Map<String, Object> filaEspana = fila(proyeccion, espana.getId());

            double espUruguay = num(filaUruguay.get("puntos_esperados"));
            double espBrasil = num(filaBrasil.get("puntos_esperados"));
            double espEspana = num(filaEspana.get("puntos_esperados"));

            // (3) Puntos esperados razonables:
            //  - Uruguay (con pendientes) suma por encima de sus puntos actuales.
            assertTrue(espUruguay > num(filaUruguay.get("puntos_actuales")),
                    "Uruguay deberia sumar puntos esperados por sus pendientes");
            //  - los equipos sin pendientes mantienen exactamente sus puntos actuales.
            assertEquals(3.0, espBrasil, 1e-6, "Brasil no tiene pendientes -> sin cambio");
            assertEquals(2.0, espEspana, 1e-6, "España no tiene pendientes -> sin cambio");
            //  - cota superior sensata: 1 (actual) + 3 pts * 5 partidos = 16 como maximo teorico.
            assertTrue(espUruguay <= 16.0 + 1e-6, "puntos esperados dentro de un rango plausible");

            // (4) La posicion proyectada cambia respecto del orden por puntos actuales:
            //     por puntos actuales Uruguay es ULTIMO (1 pto); proyectado debe escalar posiciones.
            int posProyectadaUruguay = (int) num(filaUruguay.get("posicion_proyectada"));
            assertEquals(1, posProyectadaUruguay,
                    "Uruguay debe pasar de ultimo (por puntos) a primero (proyectado)");
            assertTrue(espUruguay > espBrasil && espUruguay > espEspana,
                    "los pendientes hacen que Uruguay supere a Brasil y España");
        } finally {
            // Limpieza del escenario de prueba (deja la base como estaba).
            partidoRepo.deleteAll(pendientes);
            posicionRepo.deleteAll(List.of(pEspana, pBrasil, pUruguay));
            torneoRepo.delete(torneo);
        }
    }

    private Equipo equipo(String nombre) {
        return equipoRepo.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException("Equipo de seed ausente: " + nombre));
    }

    private Posicion posicion(Long torneoId, Long equipoId, int puntos) {
        Posicion p = new Posicion();
        p.setTorneoId(torneoId);
        p.setEquipoId(equipoId);
        p.setPuntos(puntos);
        return posicionRepo.save(p);
    }

    private static Map<String, Object> fila(List<Map<String, Object>> filas, Long equipoId) {
        return filas.stream().filter(f -> equipoId.equals(f.get("equipo_id"))).findFirst()
                .orElseThrow(() -> new AssertionError("falta la fila del equipo " + equipoId));
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }
}
