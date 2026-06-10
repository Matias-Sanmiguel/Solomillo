package dev.solomillo.rankings;

import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoDeportivo;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.repository.EventoDeportivoRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PosicionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RankingsService {

    private final PosicionRepository posicionRepo;
    private final PartidoRepository partidoRepo;
    private final JugadorRepository jugadorRepo;
    private final EventoDeportivoRepository eventoRepo;

    public RankingsService(PosicionRepository p, PartidoRepository pa,
                           JugadorRepository j, EventoDeportivoRepository e) {
        this.posicionRepo = p;
        this.partidoRepo = pa;
        this.jugadorRepo = j;
        this.eventoRepo = e;
    }

    public void actualizar(EventoInterno evento) {
        if ("gol".equals(evento.tipo()) && evento.jugadorId() != null) {
            registrarGol(evento);
        } else if ("fin_partido".equals(evento.tipo())) {
            cerrarPartido(evento.partidoId());
        }
    }

    private void registrarGol(EventoInterno evento) {
        Partido partido = partidoRepo.findById(evento.partidoId()).orElse(null);
        Jugador jugador = jugadorRepo.findById(evento.jugadorId()).orElse(null);
        if (partido == null || jugador == null) return;

        Long torneoId = partido.getTorneo().getId();
        Long anota = jugador.getEquipo().getId();
        Long rival = anota.equals(partido.getEquipoLocal().getId())
                ? partido.getEquipoVisitante().getId()
                : partido.getEquipoLocal().getId();

        posicion(torneoId, anota).setGolesFavor(posicion(torneoId, anota).getGolesFavor() + 1);
        posicion(torneoId, rival).setGolesContra(posicion(torneoId, rival).getGolesContra() + 1);
        posicionRepo.save(posicion(torneoId, anota));
        posicionRepo.save(posicion(torneoId, rival));
    }

    private void cerrarPartido(Long partidoId) {
        Partido partido = partidoRepo.findById(partidoId).orElse(null);
        if (partido == null) return;

        Long local = partido.getEquipoLocal().getId();
        Long visit = partido.getEquipoVisitante().getId();
        Long torneoId = partido.getTorneo().getId();

        List<EventoDeportivo> goles = eventoRepo.findByPartidoIdAndTipo(partidoId, "gol");
        List<Long> jugadoresLocal = jugadorRepo.findByEquipoId(local).stream().map(Jugador::getId).toList();
        List<Long> jugadoresVisit = jugadorRepo.findByEquipoId(visit).stream().map(Jugador::getId).toList();

        long gLocal = goles.stream().filter(g -> g.getJugadorId() != null && jugadoresLocal.contains(g.getJugadorId())).count();
        long gVisit = goles.stream().filter(g -> g.getJugadorId() != null && jugadoresVisit.contains(g.getJugadorId())).count();

        Posicion pLocal = posicion(torneoId, local);
        Posicion pVisit = posicion(torneoId, visit);

        if (gLocal > gVisit) {
            pLocal.setGanados(pLocal.getGanados() + 1); pLocal.setPuntos(pLocal.getPuntos() + 3);
            pVisit.setPerdidos(pVisit.getPerdidos() + 1);
        } else if (gVisit > gLocal) {
            pVisit.setGanados(pVisit.getGanados() + 1); pVisit.setPuntos(pVisit.getPuntos() + 3);
            pLocal.setPerdidos(pLocal.getPerdidos() + 1);
        } else {
            pLocal.setEmpatados(pLocal.getEmpatados() + 1); pLocal.setPuntos(pLocal.getPuntos() + 1);
            pVisit.setEmpatados(pVisit.getEmpatados() + 1); pVisit.setPuntos(pVisit.getPuntos() + 1);
        }
        posicionRepo.save(pLocal);
        posicionRepo.save(pVisit);
    }

    private Posicion posicion(Long torneoId, Long equipoId) {
        return posicionRepo.findByTorneoIdAndEquipoId(torneoId, equipoId).orElseGet(() -> {
            var p = new Posicion(); p.setTorneoId(torneoId); p.setEquipoId(equipoId);
            return posicionRepo.save(p);
        });
    }
}
