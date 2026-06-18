package dev.solomillo.ingest;

import dev.solomillo.core.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ApiFootballClient {

    private static final String BASE_URL = "https://v3.football.api-sports.io";

    private final RestClient http;

    public ApiFootballClient(AppProperties props) {
        this.http = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("x-apisports-key", props.apiFootballKey)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> ligas(Integer season) {
        Map<String, Object> body = fetch("/leagues?season=" + season);
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fixtures(int leagueId, int season) {
        Map<String, Object> body = fetch("/fixtures?league=" + leagueId + "&season=" + season);
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> eventosFixture(long fixtureId) {
        Map<String, Object> body = fetch("/fixtures/events?fixture=" + fixtureId);
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> equiposLiga(int leagueId, int season) {
        Map<String, Object> body = fetch("/teams?league=" + leagueId + "&season=" + season);
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> jugadoresEquipo(int teamId, int season) {
        Map<String, Object> body = fetch("/players?team=" + teamId + "&season=" + season + "&page=1");
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fixturesLive(int leagueId) {
        Map<String, Object> body = fetch("/fixtures?live=all&league=" + leagueId);
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fixturesByDate(String date, int leagueId, int season) {
        Map<String, Object> body = fetch("/fixtures?date=" + date + "&league=" + leagueId + "&season=" + season);
        return (List<Map<String, Object>>) body.getOrDefault("response", List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetch(String path) {
        return http.get()
                .uri(path)
                .retrieve()
                .body(Map.class);
    }
}
