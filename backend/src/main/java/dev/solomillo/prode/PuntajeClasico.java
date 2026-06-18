package dev.solomillo.prode;

import dev.solomillo.domain.Partido;
import org.springframework.stereotype.Component;

/**
 * Esquema clásico:
 *  - signo (1/X/2) correcto .................... +3
 *  - resultado exacto (extra al signo) ......... +5
 *  - diferencia de gol correcta sin exacto ..... +1
 */
@Component
public class PuntajeClasico implements PolizaPuntaje {

    private static final int SIGNO = 3;
    private static final int EXACTO_EXTRA = 5;
    private static final int DIFERENCIA_EXTRA = 1;

    @Override
    public int calcular(Pronostico pronostico, Partido partido) {
        Signo real = Signo.desde(partido.getGolesLocal(), partido.getGolesVisitante());
        if (real == null || pronostico.getSigno() != real) return 0;

        int puntos = SIGNO;

        Integer gl = pronostico.getGolesLocal();
        Integer gv = pronostico.getGolesVisitante();
        if (gl != null && gv != null) {
            boolean exacto = gl.equals(partido.getGolesLocal())
                    && gv.equals(partido.getGolesVisitante());
            if (exacto) {
                puntos += EXACTO_EXTRA;
            } else if (gl - gv == partido.getGolesLocal() - partido.getGolesVisitante()) {
                puntos += DIFERENCIA_EXTRA;
            }
        }
        return puntos;
    }

    @Override
    public String version() {
        return "clasico-v1";
    }
}
