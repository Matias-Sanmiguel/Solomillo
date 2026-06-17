package dev.solomillo.ml;

import dev.solomillo.domain.NivelTorneo;
import dev.solomillo.domain.Torneo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifica la lógica de eloratings.net (factores K, ventaja de local, dif. de goles). */
class EloRatingTest {

    @Test void multiplicadorPorDiferenciaDeGoles() {
        assertEquals(1.0, EloService.multiplicadorGoles(0));
        assertEquals(1.0, EloService.multiplicadorGoles(1));
        assertEquals(1.5, EloService.multiplicadorGoles(2));
        assertEquals(1.75, EloService.multiplicadorGoles(3));
        assertEquals(1.875, EloService.multiplicadorGoles(4)); // 1.75 + (4-3)/8
        assertEquals(2.0, EloService.multiplicadorGoles(5));   // 1.75 + (5-3)/8
        assertEquals(1.5, EloService.multiplicadorGoles(-2));  // signo indistinto
    }

    @Test void pesosKSegunNivelDeTorneo() {
        assertEquals(60, peso(NivelTorneo.MUNDIAL));
        assertEquals(40, peso(NivelTorneo.CLASIFICATORIO));
        assertEquals(20, peso(NivelTorneo.AMISTOSO));
    }

    @Test void torneoSinNivelUsaOtro() {
        var t = new Torneo();
        t.setNivel(null);
        assertEquals(30, EloService.pesoTorneo(t));
        assertEquals(30, EloService.pesoTorneo(null));
    }

    @Test void esperadoEnCanchaNeutralConRatingsIguales() {
        // sin ventaja, ratings iguales -> 0.5
        assertEquals(0.5, EloService.esperado(1500, 1500, 0.0), 1e-9);
    }

    @Test void ventajaDeLocalSubeLaExpectativa() {
        // +100 de ventaja con ratings iguales -> 1/(1+10^-0.25) ≈ 0.6401
        assertEquals(0.6401, EloService.esperado(1500, 1500, 100.0), 1e-4);
    }

    @Test void enumExponeFactorK() {
        assertEquals(50, NivelTorneo.CONTINENTAL.k());
    }

    private static int peso(NivelTorneo nivel) {
        var t = new Torneo();
        t.setNivel(nivel);
        return EloService.pesoTorneo(t);
    }
}
