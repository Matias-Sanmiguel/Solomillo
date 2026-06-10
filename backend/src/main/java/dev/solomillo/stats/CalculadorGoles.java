package dev.solomillo.stats;

import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.repository.EstadisticaEquipoRepository;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Component;

@Component
public class CalculadorGoles implements CalculadorEstadistica {

    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;
    private final EstadisticaJugadorRepository ejRepo;
    private final EstadisticaEquipoRepository eeRepo;

    public CalculadorGoles(JugadorRepository j, PartidoRepository p,
                           EstadisticaJugadorRepository ej, EstadisticaEquipoRepository ee) {
        this.jugadorRepo = j;
        this.partidoRepo = p;
        this.ejRepo = ej;
        this.eeRepo = ee;
    }

    @Override
    public boolean aplica(EventoInterno e) {
        return "gol".equals(e.tipo()) && e.jugadorId() != null;
    }

    @Override
    public void actualizar(EventoInterno e) {
        Partido partido = partidoRepo.findById(e.partidoId()).orElse(null);
        Jugador jugador = jugadorRepo.findById(e.jugadorId()).orElse(null);
        if (partido == null || jugador == null) return;

        Long torneoId = partido.getTorneo().getId();
        incJugador(jugador.getId(), torneoId, "goles", 1);
        incEquipo(jugador.getEquipo().getId(), torneoId, "goles", 1);
    }

    private void incJugador(Long jId, Long tId, String metrica, double delta) {
        var stat = ejRepo.findByJugadorIdAndTorneoIdAndMetrica(jId, tId, metrica)
                .orElseGet(() -> { var s = new EstadisticaJugador(); s.setJugadorId(jId); s.setTorneoId(tId); s.setMetrica(metrica); return s; });
        stat.setValor(stat.getValor() + delta);
        ejRepo.save(stat);
    }

    private void incEquipo(Long eId, Long tId, String metrica, double delta) {
        var stat = eeRepo.findByEquipoIdAndTorneoIdAndMetrica(eId, tId, metrica)
                .orElseGet(() -> { var s = new EstadisticaEquipo(); s.setEquipoId(eId); s.setTorneoId(tId); s.setMetrica(metrica); return s; });
        stat.setValor(stat.getValor() + delta);
        eeRepo.save(stat);
    }
}
