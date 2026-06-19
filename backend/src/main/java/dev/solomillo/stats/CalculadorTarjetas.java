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
public class CalculadorTarjetas implements CalculadorEstadistica {

    private final JugadorRepository jugadorRepo;
    private final PartidoRepository partidoRepo;
    private final EstadisticaJugadorRepository ejRepo;
    private final EstadisticaEquipoRepository eeRepo;

    public CalculadorTarjetas(JugadorRepository j, PartidoRepository p,
                              EstadisticaJugadorRepository ej, EstadisticaEquipoRepository ee) {
        this.jugadorRepo = j;
        this.partidoRepo = p;
        this.ejRepo = ej;
        this.eeRepo = ee;
    }

    @Override
    public boolean aplica(EventoInterno e) {
        if (!"tarjeta".equals(e.tipo())) return false;
        return e.jugadorId() != null
                || (e.datos() != null && (e.datos().containsKey("color") || e.datos().containsKey("eventType")));
    }

    @Override
    public void actualizar(EventoInterno e) {
        Partido partido = partidoRepo.findById(e.partidoId()).orElse(null);
        if (partido == null) return;

        Long torneoId = partido.getTorneo().getId();
        String especifica = metricaEspecifica(e);

        if (e.jugadorId() != null) {
            Jugador jugador = jugadorRepo.findById(e.jugadorId()).orElse(null);
            if (jugador == null) return;
            Long equipoId = jugador.getEquipo().getId();
            incJugador(e.jugadorId(), torneoId, "tarjetas", 1);
            incEquipo(equipoId, torneoId, "tarjetas", 1);
            if (especifica != null) {
                incJugador(e.jugadorId(), torneoId, especifica, 1);
                incEquipo(equipoId, torneoId, especifica, 1);
            }
        } else if (e.datos() != null && e.datos().containsKey("equipo")) {
            String equipoStr = (String) e.datos().get("equipo");
            Long equipoId = "visitante".equals(equipoStr)
                    ? partido.getEquipoVisitante().getId()
                    : partido.getEquipoLocal().getId();
            String metrica = especifica != null ? especifica : "tarjetas_amarillas";
            incEquipo(equipoId, torneoId, metrica, 1);
        }
    }

    private String metricaEspecifica(EventoInterno e) {
        if (e.datos() == null) return null;
        Object color = e.datos().get("color");
        if (color != null) return "roja".equals(color) ? "tarjetas_rojas" : "tarjetas_amarillas";
        Object tipo = e.datos().get("eventType");
        if (tipo != null) return switch (tipo.toString()) {
            case "YELLOW_CARD" -> "tarjetas_amarillas";
            case "RED_CARD" -> "tarjetas_rojas";
            default -> null;
        };
        return null;
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
