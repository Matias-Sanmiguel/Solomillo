package dev.solomillo.stats;

import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.repository.EstadisticaEquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Component;

@Component
public class CalculadorPartidosJugados implements CalculadorEstadistica {

    private final PartidoRepository partidoRepo;
    private final EstadisticaEquipoRepository eeRepo;

    public CalculadorPartidosJugados(PartidoRepository partidoRepo, EstadisticaEquipoRepository eeRepo) {
        this.partidoRepo = partidoRepo;
        this.eeRepo = eeRepo;
    }

    @Override
    public boolean aplica(EventoInterno e) {
        return "fin_partido".equals(e.tipo());
    }

    @Override
    public void actualizar(EventoInterno e) {
        Partido partido = partidoRepo.findById(e.partidoId()).orElse(null);
        if (partido == null) return;

        Long torneoId = partido.getTorneo().getId();
        incEquipo(partido.getEquipoLocal().getId(), torneoId);
        incEquipo(partido.getEquipoVisitante().getId(), torneoId);
    }

    private void incEquipo(Long eId, Long tId) {
        var stat = eeRepo.findByEquipoIdAndTorneoIdAndMetrica(eId, tId, "partidos_jugados")
                .orElseGet(() -> { var s = new EstadisticaEquipo(); s.setEquipoId(eId); s.setTorneoId(tId); s.setMetrica("partidos_jugados"); return s; });
        stat.setValor(stat.getValor() + 1);
        eeRepo.save(stat);
    }
}
