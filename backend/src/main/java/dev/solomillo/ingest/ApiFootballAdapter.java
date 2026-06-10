package dev.solomillo.ingest;

import dev.solomillo.events.EventoInterno;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("api-football")
public class ApiFootballAdapter implements FuenteAdapter {

    private static final Map<String, String> TIPOS = Map.of(
            "Goal", "gol",
            "Card", "tarjeta",
            "subst", "sustitucion",
            "Var", "var",
            "Penalty", "penalti"
    );

    @Override
    @SuppressWarnings("unchecked")
    public EventoInterno normalizar(Map<String, Object> payload) {
        String externo = (String) payload.getOrDefault("type", "");

        Map<String, Object> time = (Map<String, Object>) payload.getOrDefault("time", Map.of());
        Map<String, Object> player = (Map<String, Object>) payload.getOrDefault("player", Map.of());
        Map<String, Object> fixture = (Map<String, Object>) payload.getOrDefault("fixture", Map.of());

        Object fixtureId = fixture.getOrDefault("id", payload.get("fixture_id"));
        Object elapsed = time.getOrDefault("elapsed", payload.getOrDefault("minute", 0));
        Object playerId = player.get("id");

        return new EventoInterno(
                TIPOS.getOrDefault(externo, externo.toLowerCase()),
                fixtureId != null ? ((Number) fixtureId).longValue() : null,
                elapsed != null ? ((Number) elapsed).intValue() : 0,
                playerId != null ? ((Number) playerId).longValue() : null,
                payload
        );
    }
}
