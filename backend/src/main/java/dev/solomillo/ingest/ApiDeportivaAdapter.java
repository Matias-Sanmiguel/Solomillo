package dev.solomillo.ingest;

import dev.solomillo.events.EventoInterno;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component("api-deportiva")
public class ApiDeportivaAdapter implements FuenteAdapter {

    private static final Map<String, String> TIPOS = Map.of(
            "GOAL", "gol",
            "SCORE", "gol",
            "YELLOW_CARD", "tarjeta",
            "RED_CARD", "tarjeta",
            "MATCH_END", "fin_partido"
    );

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ApiDeportivaAdapter(
            RestTemplate restTemplate,
            @Value("${api-deportiva.base-url:https://api.deportiva.example/v1}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public EventoInterno normalizar(Map<String, Object> payload) {
        String externo = (String) payload.getOrDefault("eventType", "");
        Map<String, Object> player = (Map<String, Object>) payload.getOrDefault("player", Map.of());

        Long jugadorId = toLong(player.get("id"));
        // La fuente puede mandar sólo el código externo del jugador; lo resolvemos contra su API.
        if (jugadorId == null && player.get("ref") != null) {
            jugadorId = resolverJugador(player.get("ref").toString());
        }

        return new EventoInterno(
                TIPOS.getOrDefault(externo, "otro"),
                toLong(payload.get("fixtureId")),
                toInt(payload.getOrDefault("minute", 0)),
                jugadorId,
                payload
        );
    }

    private Long resolverJugador(String ref) {
        try {
            Map<String, Object> res = restTemplate.getForObject(
                    baseUrl + "/players/{ref}", Map.class, ref);
            return res == null ? null : toLong(res.get("id"));
        } catch (RestClientException e) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static int toInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }
}
