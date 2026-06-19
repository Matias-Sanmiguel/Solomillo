package dev.solomillo.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {
    @Value("${app.jwt.secret}") public String jwtSecret;
    @Value("${app.jwt.expiration-ms}") public long jwtExpirationMs;
    @Value("${app.models-dir}") public String modelsDir;
    @Value("${app.api-football.key:}") public String apiFootballKey;
    @Value("${app.football-data.key:}") public String footballDataKey;
}
