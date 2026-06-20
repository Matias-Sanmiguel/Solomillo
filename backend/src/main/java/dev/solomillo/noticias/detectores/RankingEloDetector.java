package dev.solomillo.noticias.detectores;

import dev.solomillo.domain.Equipo;
import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.ContextoNoticia;
import dev.solomillo.noticias.DetectorNoticia;
import dev.solomillo.noticias.Noticia;
import dev.solomillo.repository.EquipoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranking Elo: destaca a las selecciones del Top 5 del ranking Elo actual.
 * Es un detector global (modo batch): se regenera tras cada avance de simulación
 * para reflejar los movimientos del ranking. Ej.: "Uruguay entra al Top 5 del ranking Elo".
 */
@Component
public class RankingEloDetector implements DetectorNoticia {

    private static final int TOP = 5;

    private final EquipoRepository equipoRepo;

    public RankingEloDetector(EquipoRepository equipoRepo) {
        this.equipoRepo = equipoRepo;
    }

    @Override
    public List<Noticia> detectar(ContextoNoticia ctx) {
        // Detector global: sólo corre en regeneración batch, no por cada partido.
        if (!ctx.esBatch()) return List.of();

        List<Equipo> top = equipoRepo.findAll().stream()
                .filter(e -> e.getElo() != null)
                .sorted(Comparator.comparingDouble(Equipo::getElo).reversed())
                .limit(TOP)
                .toList();

        List<Noticia> out = new ArrayList<>();
        int pos = 1;
        for (Equipo e : top) {
            Noticia n = new Noticia();
            n.setCategoria(CategoriaNoticia.RANKING_ELO);
            String posTxt = pos == 1 ? "lidera el ranking Elo" : "está N° " + pos + " del ranking Elo";
            n.setTitulo(e.getNombre() + " " + posTxt);
            n.setSubtitulo("Top " + TOP + " del Mundial · Elo " + Math.round(e.getElo()));
            n.setResumen(e.getNombre() + " se ubica en el puesto " + pos + " del ranking Elo con "
                    + Math.round(e.getElo()) + " puntos, entre las mejores selecciones del torneo.");
            n.setFecha(LocalDateTime.now());
            n.setRelevancia(pos == 1 ? 65 : 56 - pos);
            n.setOrigen(ctx.origen());
            n.setImagenTipo("RANKING");
            n.setEquipoLocalId(e.getId());
            n.setTags(List.of(e.getNombre(), "Ranking Elo", "Top " + TOP));
            n.setClaveNatural("RANKING_ELO:equipo=" + e.getId());
            out.add(n);
            pos++;
        }
        return out;
    }
}
