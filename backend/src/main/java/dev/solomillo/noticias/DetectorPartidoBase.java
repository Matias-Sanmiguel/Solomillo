package dev.solomillo.noticias;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.repository.PartidoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Base para detectores que trabajan partido por partido. Resuelve el modo
 * evento (un único partido) vs. el modo batch (todos los partidos finalizados
 * del torneo), de modo que el subtipo sólo implemente {@link #paraPartido}.
 */
public abstract class DetectorPartidoBase implements DetectorNoticia {

    protected final PartidoRepository partidoRepo;

    protected DetectorPartidoBase(PartidoRepository partidoRepo) {
        this.partidoRepo = partidoRepo;
    }

    @Override
    public List<Noticia> detectar(ContextoNoticia ctx) {
        List<Noticia> out = new ArrayList<>();
        for (Partido p : partidos(ctx)) {
            if (p.getEquipoLocal() == null || p.getEquipoVisitante() == null) continue;
            if (p.getGolesLocal() == null || p.getGolesVisitante() == null) continue;
            out.addAll(paraPartido(p, ctx));
        }
        return out;
    }

    private List<Partido> partidos(ContextoNoticia ctx) {
        if (!ctx.esBatch()) return List.of(ctx.partido());
        return partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.FINALIZADO).stream()
                .filter(p -> p.getTorneo() != null && ctx.torneoId().equals(p.getTorneo().getId()))
                .toList();
    }

    /** Noticias generadas para un partido concreto (puede ser vacío). */
    protected abstract List<Noticia> paraPartido(Partido p, ContextoNoticia ctx);
}
