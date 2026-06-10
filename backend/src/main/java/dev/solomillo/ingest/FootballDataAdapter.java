package dev.solomillo.ingest;

import dev.solomillo.events.EventoInterno;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("football-data")
public class FootballDataAdapter implements FuenteAdapter {

    private static final Map<String, String> TIPOS = Map.of(
            "GOAL", "gol",
            "CARD", "tarjeta",
            "SUBSTITUTION", "sustitucion",
            "FULL_TIME", "fin_partido"
    );

    @Override
    @SuppressWarnings("unchecked")
    public EventoInterno normalizar(Map<String, Object> payload) {
        String externo = (String) payload.getOrDefault("type", "");
        Map<String, Object> match = (Map<String, Object>) payload.getOrDefault("match", Map.of());
        Map<String, Object> scorer = (Map<String, Object>) payload.getOrDefault("scorer",
                payload.getOrDefault("player", Map.of()));

        Object matchId = match.getOrDefault("id", payload.get("match_id"));
        return new EventoInterno(
                TIPOS.getOrDefault(externo, externo.toLowerCase()),
                matchId != null ? ((Number) matchId).longValue() : null,
                scorer != null ? ((Number) payload.getOrDefault("minute", 0)).intValue() : 0,
                scorer != null && scorer.get("id") != null ? ((Number) scorer.get("id")).longValue() : null,
                payload
        );
    }
}
