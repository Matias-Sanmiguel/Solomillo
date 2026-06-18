package dev.solomillo.prode;

import dev.solomillo.domain.Partido;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PuntajeClasicoTest {

    private final PuntajeClasico poliza = new PuntajeClasico();

    private static Partido partido(Integer golesLocal, Integer golesVisitante) {
        var p = new Partido();
        p.setGolesLocal(golesLocal);
        p.setGolesVisitante(golesVisitante);
        return p;
    }

    private static Pronostico pronostico(Signo signo, Integer golesLocal, Integer golesVisitante) {
        var p = new Pronostico();
        p.setSigno(signo);
        p.setGolesLocal(golesLocal);
        p.setGolesVisitante(golesVisitante);
        return p;
    }

    @Test void signoCorrectoSinExacto() {
        // acierta 1/X/2 pero sin cargar resultado exacto -> 3
        assertEquals(3, poliza.calcular(pronostico(Signo.LOCAL, null, null), partido(2, 0)));
    }

    @Test void resultadoExacto() {
        // signo + marcador exacto -> 3 + 5 = 8
        assertEquals(8, poliza.calcular(pronostico(Signo.LOCAL, 2, 1), partido(2, 1)));
    }

    @Test void empateExacto() {
        assertEquals(8, poliza.calcular(pronostico(Signo.EMPATE, 1, 1), partido(1, 1)));
    }

    @Test void signoCorrectoDiferenciaCorrectaNoExacto() {
        // gana local por 1 (pronostica 1-0, real 2-1) -> 3 + 1 = 4
        assertEquals(4, poliza.calcular(pronostico(Signo.LOCAL, 1, 0), partido(2, 1)));
    }

    @Test void signoCorrectoDistintaDiferencia() {
        // acierta el signo pero ni exacto ni diferencia -> 3
        assertEquals(3, poliza.calcular(pronostico(Signo.LOCAL, 3, 0), partido(2, 1)));
    }

    @Test void signoIncorrecto() {
        assertEquals(0, poliza.calcular(pronostico(Signo.VISITANTE, null, null), partido(2, 0)));
    }

    @Test void partidoSinResultadoNoPuntua() {
        assertEquals(0, poliza.calcular(pronostico(Signo.LOCAL, 1, 0), partido(null, null)));
    }
}
