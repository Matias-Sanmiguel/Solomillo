package dev.solomillo.ingest;

import dev.solomillo.core.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class FootballDataClient {

    private static final String BASE_URL = "https://api.football-data.org/v4";
    private static final String WC = "WC";

    private final RestClient http;

    public FootballDataClient(AppProperties props) {
        this.http = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-Auth-Token", props.footballDataKey)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> matchesLive() {
        Map<String, Object> body = fetch("/competitions/" + WC + "/matches?status=IN_PLAY");
        return (List<Map<String, Object>>) body.getOrDefault("matches", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> matchesPaused() {
        Map<String, Object> body = fetch("/competitions/" + WC + "/matches?status=PAUSED");
        return (List<Map<String, Object>>) body.getOrDefault("matches", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> matchesByDate(String date) {
        Map<String, Object> body = fetch("/competitions/" + WC + "/matches?dateFrom=" + date + "&dateTo=" + date);
        return (List<Map<String, Object>>) body.getOrDefault("matches", List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetch(String path) {
        return http.get()
                .uri(path)
                .retrieve()
                .body(Map.class);
    }
}
