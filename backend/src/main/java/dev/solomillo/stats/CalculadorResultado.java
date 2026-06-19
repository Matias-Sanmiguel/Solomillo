package dev.solomillo.stats;

import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.repository.EstadisticaEquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Component;

/**
 * Tercera estrategia de estadística (patrón Strategy): al finalizar el partido deriva
 * las métricas "futboleras" de cada equipo a partir del marcador final:
 * partidos jugados, victorias/empates/derrotas, goles a favor/contra y puntos.
 *
 * Se engancha al evento {@code fin_partido} que ya emite el flujo real (en vivo y seed),
 * por lo que no toca el motor ni los otros calculadores.
 */
@Component
public class CalculadorResultado implements CalculadorEstadistica {

    private final PartidoRepository partidoRepo;
    private final EstadisticaEquipoRepository eeRepo;

    public CalculadorResultado(PartidoRepository p, EstadisticaEquipoRepository ee) {
        this.partidoRepo = p;
        this.eeRepo = ee;
    }

    @Override
    public boolean aplica(EventoInterno e) {
        return "fin_partido".equals(e.tipo());
    }

    @Override
    public void actualizar(EventoInterno e) {
        Partido p = partidoRepo.findById(e.partidoId()).orElse(null);
        if (p == null || (p.getGolesLocal() == null && p.getGolesVisitante() == null)) return;

        Long torneoId = p.getTorneo().getId();
        int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
        int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();

        acumular(p.getEquipoLocal().getId(), torneoId, gl, gv);
        acumular(p.getEquipoVisitante().getId(), torneoId, gv, gl);
    }

    /** Acumula las métricas de un equipo dado sus goles a favor y en contra en el partido. */
    private void acumular(Long equipoId, Long torneoId, int golesFavor, int golesContra) {
        inc(equipoId, torneoId, "partidos_jugados", 1);
        inc(equipoId, torneoId, "goles_favor", golesFavor);
        inc(equipoId, torneoId, "goles_contra", golesContra);

        if (golesFavor > golesContra) {
            inc(equipoId, torneoId, "victorias", 1);
            inc(equipoId, torneoId, "puntos", 3);
        } else if (golesFavor == golesContra) {
            inc(equipoId, torneoId, "empates", 1);
            inc(equipoId, torneoId, "puntos", 1);
        } else {
            inc(equipoId, torneoId, "derrotas", 1);
        }
    }

    private void inc(Long eId, Long tId, String metrica, double delta) {
        var stat = eeRepo.findByEquipoIdAndTorneoIdAndMetrica(eId, tId, metrica)
                .orElseGet(() -> { var s = new EstadisticaEquipo(); s.setEquipoId(eId); s.setTorneoId(tId); s.setMetrica(metrica); return s; });
        stat.setValor(stat.getValor() + delta);
        eeRepo.save(stat);
    }
}
