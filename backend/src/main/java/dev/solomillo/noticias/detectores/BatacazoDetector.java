package dev.solomillo.noticias.detectores;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Ronda;
import dev.solomillo.ml.FeatureExtractor;
import dev.solomillo.ml.Prediccion;
import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.ContextoNoticia;
import dev.solomillo.noticias.DetectorPartidoBase;
import dev.solomillo.noticias.Noticia;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PrediccionRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batacazo: una selección poco favorita DERROTA (o elimina) a una favorita.
 * Se dispara cuando el modelo daba al favorito >= 0.55 de victoria y aun así perdió.
 * Ej.: "Japón eliminó a Brasil contra todos los pronósticos".
 */
@Component
public class BatacazoDetector extends DetectorPartidoBase {

    private static final double UMBRAL_FAVORITO = 0.55;

    private final PrediccionRepository prediccionRepo;

    public BatacazoDetector(PartidoRepository partidoRepo,
                            PrediccionRepository prediccionRepo) {
        super(partidoRepo);
        this.prediccionRepo = prediccionRepo;
    }

    @Override
    protected List<Noticia> paraPartido(Partido p, ContextoNoticia ctx) {
        Prediccion pred = prediccionRepo.findFirstByPartidoIdOrderByPredichoEnDesc(p.getId()).orElse(null);
        if (pred == null) return List.of();

        double pl = pred.getProbLocal(), pv = pred.getProbVisitante();
        boolean favLocal = pl >= pv;
        double favProb = Math.max(pl, pv);
        int real = (int) FeatureExtractor.etiqueta(p);

        boolean favoritoPerdio = (favLocal && real == 2) || (!favLocal && real == 0);
        if (favProb < UMBRAL_FAVORITO || !favoritoPerdio) return List.of();

        Equipo favorito = favLocal ? p.getEquipoLocal() : p.getEquipoVisitante();
        Equipo sorpresa = favLocal ? p.getEquipoVisitante() : p.getEquipoLocal();
        boolean eliminacion = p.getRonda() != null && p.getRonda() != Ronda.GRUPOS;
        String verbo = eliminacion ? "eliminó a" : "dio el batacazo y venció a";

        Noticia n = new Noticia();
        n.setCategoria(CategoriaNoticia.BATACAZO);
        n.setTitulo(sorpresa.getNombre() + " " + verbo + " " + favorito.getNombre()
                + " contra todos los pronósticos");
        n.setSubtitulo("El favorito tenía " + pct(favProb) + " de chances según el modelo");
        n.setResumen(sorpresa.getNombre() + " protagonizó el batacazo del torneo al vencer "
                + p.getGolesLocal() + "-" + p.getGolesVisitante() + " a " + favorito.getNombre()
                + ", a quien el modelo daba " + pct(favProb) + " de victoria.");
        n.setFecha(p.getFechaHora());
        n.setRelevancia(eliminacion ? 95 : 88);
        n.setOrigen(ctx.origen());
        n.setImagenTipo("PARTIDO");
        n.setPartidoId(p.getId());
        n.setEquipoLocalId(p.getEquipoLocal().getId());
        n.setEquipoVisitanteId(p.getEquipoVisitante().getId());
        n.setMarcador(p.getGolesLocal() + "-" + p.getGolesVisitante());
        n.setTags(List.of(sorpresa.getNombre(), favorito.getNombre(), "Batacazo"));
        n.setClaveNatural("BATACAZO:partido=" + p.getId());
        return List.of(n);
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
