package dev.solomillo.noticias.detectores;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Partido;
import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.ContextoNoticia;
import dev.solomillo.noticias.DetectorPartidoBase;
import dev.solomillo.noticias.Noticia;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Goleada: victoria con diferencia de gol amplia (>= 3).
 * Ej.: "Argentina aplastó 5-0 a Panamá".
 */
@Component
public class GoleadaDetector extends DetectorPartidoBase {

    private static final int DIFERENCIA_MIN = 3;

    public GoleadaDetector(PartidoRepository partidoRepo) {
        super(partidoRepo);
    }

    @Override
    protected List<Noticia> paraPartido(Partido p, ContextoNoticia ctx) {
        int gl = p.getGolesLocal(), gv = p.getGolesVisitante();
        int dif = Math.abs(gl - gv);
        if (dif < DIFERENCIA_MIN) return List.of();

        boolean ganaLocal = gl > gv;
        Equipo ganador = ganaLocal ? p.getEquipoLocal() : p.getEquipoVisitante();
        Equipo perdedor = ganaLocal ? p.getEquipoVisitante() : p.getEquipoLocal();
        int golesG = Math.max(gl, gv), golesP = Math.min(gl, gv);

        String verbo = dif >= 5 ? "goleó sin piedad" : dif >= 4 ? "aplastó" : "se impuso con autoridad";

        Noticia n = new Noticia();
        n.setCategoria(CategoriaNoticia.GOLEADA);
        n.setTitulo(ganador.getNombre() + " " + verbo + " " + golesG + "-" + golesP
                + " a " + perdedor.getNombre());
        n.setSubtitulo("Diferencia de " + dif + " goles en un partido de un solo lado");
        n.setResumen(ganador.getNombre() + " firmó una actuación contundente y venció "
                + golesG + "-" + golesP + " a " + perdedor.getNombre()
                + ", dejando una imagen demoledora en el torneo.");
        n.setFecha(p.getFechaHora());
        n.setRelevancia(60 + dif * 5);
        n.setOrigen(ctx.origen());
        n.setImagenTipo("PARTIDO");
        n.setPartidoId(p.getId());
        n.setEquipoLocalId(p.getEquipoLocal().getId());
        n.setEquipoVisitanteId(p.getEquipoVisitante().getId());
        n.setMarcador(gl + "-" + gv);
        n.setTags(List.of(ganador.getNombre(), perdedor.getNombre(), "Goleada"));
        n.setClaveNatural("GOLEADA:partido=" + p.getId());
        return List.of(n);
    }
}
