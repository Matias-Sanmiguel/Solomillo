package dev.solomillo.ingest;

import dev.solomillo.events.EventoInterno;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("ejemplo")
public class EjemploAdapter implements FuenteAdapter {

    @Override
    public EventoInterno normalizar(Map<String, Object> payload) {
        return new EventoInterno(
                (String) payload.get("type"),
                toLong(payload.get("match_id")),
                toInt(payload.getOrDefault("minute", 0)),
                toLong(payload.get("player_id")),
                payload
        );
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static int toInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }
}
