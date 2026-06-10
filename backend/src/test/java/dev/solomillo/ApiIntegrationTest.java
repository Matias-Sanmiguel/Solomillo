package dev.solomillo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ApiIntegrationTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = System.getProperty("baseUrl", "http://localhost:8000");
    }

    @Test void health() {
        get("/health").then().statusCode(200).body("status", is("ok"));
    }

    @Test void seedEquipos() {
        get("/equipos").then().statusCode(200)
                .body("nombre", hasItems("Argentina", "Francia", "Croacia", "Marruecos"));
    }

    @Test void ingestRequiresAdmin() {
        given().contentType(ContentType.JSON)
                .body(Map.of("type", "gol", "match_id", 1, "minute", 5, "player_id", 1))
                .post("/ingest/ejemplo")
                .then().statusCode(anyOf(is(401), is(403)));
    }

    @Test void ingestUpdatesStats() {
        String tok = adminToken();
        float antes = statGoles(1);
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + tok)
                .body(Map.of("type", "gol", "match_id", 1, "minute", 12, "player_id", 1))
                .post("/ingest/ejemplo")
                .then().statusCode(202);
        assertThat(statGoles(1), is(antes + 1));
    }

    @Test void footballDataAdapter() {
        String tok = adminToken();
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + tok)
                .body(Map.of("type", "GOAL", "match", Map.of("id", 1), "minute", 50, "scorer", Map.of("id", 3)))
                .post("/ingest/football-data")
                .then().statusCode(202);
    }

    @Test void mlTrainAndPredict() {
        String tok = dsToken();
        given().header("Authorization", "Bearer " + tok).post("/ml/modelos/entrenar").then().statusCode(201);
        given().header("Authorization", "Bearer " + tok).post("/ml/modelos/entrenar-rendimiento").then().statusCode(201);

        get("/ml/predicciones/1").then().statusCode(200).body("probabilidades", notNullValue());
        get("/ml/rendimiento/1").then().statusCode(200)
                .body("rating_esperado", greaterThanOrEqualTo(1f))
                .body("rating_esperado", lessThanOrEqualTo(10f));
        get("/ml/proyeccion/1").then().statusCode(200).body("[0].posicion_proyectada", is(1));
        get("/ml/tendencias/1").then().statusCode(200);
    }

    private String adminToken() {
        return registerToken("admin_sistema");
    }

    private String dsToken() {
        return registerToken("cientifico_datos");
    }

    private String registerToken(String rol) {
        String email = UUID.randomUUID() + "@test.dev";
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "nombre", "T", "password", "secret123", "rol", rol))
                .post("/auth/register")
                .then().extract().path("access_token");
    }

    private float statGoles(long jugadorId) {
        var stats = get("/jugadores/" + jugadorId + "/estadisticas").jsonPath().<Map<String, Object>>getList("$");
        return stats.stream().filter(s -> "goles".equals(s.get("metrica")))
                .map(s -> ((Number) s.get("valor")).floatValue()).findFirst().orElse(0f);
    }
}
