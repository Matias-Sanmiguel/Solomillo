package dev.solomillo.stats;

import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Component;

@Component
public class CalculadorTarjetas implements CalculadorEstadistica {

    private final PartidoRepository partidoRepo;
    private final EstadisticaJugadorRepository ejRepo;

    public CalculadorTarjetas(PartidoRepository partidoRepo, EstadisticaJugadorRepository ejRepo) {
        this.partidoRepo = partidoRepo;
        this.ejRepo = ejRepo;
    }

    @Override
    public boolean aplica(EventoInterno e) {
        return "tarjeta".equals(e.tipo()) && e.jugadorId() != null;
    }

    @Override
    public void actualizar(EventoInterno e) {
        String metrica = metricaPara(e.datos() == null ? null : e.datos().get("color"));
        if (metrica == null) return;

        Partido partido = partidoRepo.findById(e.partidoId()).orElse(null);
        if (partido == null) return;

        incJugador(e.jugadorId(), partido.getTorneo().getId(), metrica);
    }

    private String metricaPara(Object color) {
        if (color == null) return null;
        return switch (color.toString()) {
            case "amarilla" -> "tarjetas_amarillas";
            case "roja" -> "tarjetas_rojas";
            default -> null;
        };
    }

    private void incJugador(Long jId, Long tId, String metrica) {
        var stat = ejRepo.findByJugadorIdAndTorneoIdAndMetrica(jId, tId, metrica)
                .orElseGet(() -> { var s = new EstadisticaJugador(); s.setJugadorId(jId); s.setTorneoId(tId); s.setMetrica(metrica); return s; });
        stat.setValor(stat.getValor() + 1);
        ejRepo.save(stat);
    }
}