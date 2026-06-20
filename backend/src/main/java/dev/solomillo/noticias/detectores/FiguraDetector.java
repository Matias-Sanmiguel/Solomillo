package dev.solomillo.noticias.detectores;

import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.events.EventoDeportivo;
import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.ContextoNoticia;
import dev.solomillo.noticias.DetectorPartidoBase;
import dev.solomillo.noticias.Noticia;
import dev.solomillo.repository.EventoDeportivoRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Figura del partido: jugadores con doblete (2) o hat-trick (3+) goles en un
 * mismo partido. Cuenta los eventos "gol" atribuidos a cada jugador.
 */
@Component
public class FiguraDetector extends DetectorPartidoBase {

    private final EventoDeportivoRepository eventoRepo;
    private final JugadorRepository jugadorRepo;

    public FiguraDetector(PartidoRepository partidoRepo,
                          EventoDeportivoRepository eventoRepo,
                          JugadorRepository jugadorRepo) {
        super(partidoRepo);
        this.eventoRepo = eventoRepo;
        this.jugadorRepo = jugadorRepo;
    }

    @Override
    protected List<Noticia> paraPartido(Partido p, ContextoNoticia ctx) {
        Map<Long, Long> golesPorJugador = new LinkedHashMap<>();
        for (EventoDeportivo e : eventoRepo.findByPartidoIdAndTipo(p.getId(), "gol")) {
            if (e.getJugadorId() != null) golesPorJugador.merge(e.getJugadorId(), 1L, Long::sum);
        }

        List<Noticia> out = new ArrayList<>();
        for (var entry : golesPorJugador.entrySet()) {
            long goles = entry.getValue();
            if (goles < 2) continue;
            Jugador j = jugadorRepo.findById(entry.getKey()).orElse(null);
            if (j == null) continue;

            boolean hatTrick = goles >= 3;
            String hazaña = hatTrick ? "hat-trick" : "doblete";

            Noticia n = new Noticia();
            n.setCategoria(CategoriaNoticia.FIGURA);
            n.setTitulo(j.getNombre() + ", figura con " + hazaña
                    + " ante " + rival(p, j));
            n.setSubtitulo(goles + " goles para guiar a " + equipoNombre(j));
            n.setResumen(j.getNombre() + " fue la gran figura del partido con un "
                    + hazaña + " (" + goles + " goles) que marcó el rumbo del encuentro.");
            n.setFecha(p.getFechaHora());
            n.setRelevancia(hatTrick ? 85 : 70);
            n.setOrigen(ctx.origen());
            n.setImagenTipo("JUGADOR");
            n.setPartidoId(p.getId());
            n.setEquipoLocalId(p.getEquipoLocal().getId());
            n.setEquipoVisitanteId(p.getEquipoVisitante().getId());
            n.setJugadorId(j.getId());
            n.setMarcador(p.getGolesLocal() + "-" + p.getGolesVisitante());
            n.setTags(List.of(j.getNombre(), equipoNombre(j), hazaña));
            n.setClaveNatural("FIGURA:partido=" + p.getId() + ":jugador=" + j.getId());
            out.add(n);
        }
        return out;
    }

    private String equipoNombre(Jugador j) {
        return j.getEquipo() != null ? j.getEquipo().getNombre() : "su selección";
    }

    private String rival(Partido p, Jugador j) {
        Long suEquipo = j.getEquipo() != null ? j.getEquipo().getId() : null;
        Long localId = p.getEquipoLocal().getId();
        return localId.equals(suEquipo) ? p.getEquipoVisitante().getNombre()
                : p.getEquipoLocal().getNombre();
    }
}
