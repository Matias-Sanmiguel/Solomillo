package dev.solomillo.noticias.detectores;

import dev.solomillo.domain.Partido;
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
 * Resultado inesperado: el modelo daba a un equipo como claro favorito a ganar
 * (prob >= 0.60) pero el partido terminó EMPATADO.
 * Ej.: "España empató con Cabo Verde en un sorpresivo debut".
 * (Si el favorito directamente pierde, lo cubre el {@code BatacazoDetector}.)
 */
@Component
public class ResultadoInesperadoDetector extends DetectorPartidoBase {

    private static final double UMBRAL_FAVORITO = 0.60;

    private final PrediccionRepository prediccionRepo;

    public ResultadoInesperadoDetector(PartidoRepository partidoRepo,
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

        // Favorito claro a ganar, pero terminó en empate.
        if (favProb < UMBRAL_FAVORITO || real != 1) return List.of();

        var favorito = favLocal ? p.getEquipoLocal() : p.getEquipoVisitante();
        var otro = favLocal ? p.getEquipoVisitante() : p.getEquipoLocal();

        Noticia n = new Noticia();
        n.setCategoria(CategoriaNoticia.RESULTADO_INESPERADO);
        n.setTitulo(favorito.getNombre() + " empató con " + otro.getNombre()
                + " en un sorpresivo resultado");
        n.setSubtitulo("El modelo le daba " + pct(favProb) + " de victoria al favorito");
        n.setResumen("Las predicciones colocaban a " + favorito.getNombre() + " como amplio favorito ("
                + pct(favProb) + "), pero " + otro.getNombre() + " le sacó un empate "
                + p.getGolesLocal() + "-" + p.getGolesVisitante() + " que sacude al torneo.");
        n.setFecha(p.getFechaHora());
        n.setRelevancia(75);
        n.setOrigen(ctx.origen());
        n.setImagenTipo("PARTIDO");
        n.setPartidoId(p.getId());
        n.setEquipoLocalId(p.getEquipoLocal().getId());
        n.setEquipoVisitanteId(p.getEquipoVisitante().getId());
        n.setMarcador(p.getGolesLocal() + "-" + p.getGolesVisitante());
        n.setTags(List.of(favorito.getNombre(), otro.getNombre(), "Sorpresa"));
        n.setClaveNatural("RESULTADO_INESPERADO:partido=" + p.getId());
        return List.of(n);
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
