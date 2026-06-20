package dev.solomillo.noticias.detectores;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Jugador;
import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.ContextoNoticia;
import dev.solomillo.noticias.DetectorNoticia;
import dev.solomillo.noticias.Noticia;
import dev.solomillo.rankings.Posicion;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.EstadisticaJugadorRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PosicionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Récords del torneo (detector global, modo batch): máximo goleador, equipo
 * invicto, mejor defensa y máximo equipo goleador. Se regeneran tras cada
 * avance de simulación.
 */
@Component
public class RecordDetector implements DetectorNoticia {

    private final EstadisticaJugadorRepository ejRepo;
    private final JugadorRepository jugadorRepo;
    private final PosicionRepository posicionRepo;
    private final EquipoRepository equipoRepo;

    public RecordDetector(EstadisticaJugadorRepository ejRepo, JugadorRepository jugadorRepo,
                          PosicionRepository posicionRepo, EquipoRepository equipoRepo) {
        this.ejRepo = ejRepo;
        this.jugadorRepo = jugadorRepo;
        this.posicionRepo = posicionRepo;
        this.equipoRepo = equipoRepo;
    }

    @Override
    public List<Noticia> detectar(ContextoNoticia ctx) {
        if (!ctx.esBatch()) return List.of();
        Long torneoId = ctx.torneoId();
        List<Noticia> out = new ArrayList<>();

        maximoGoleador(torneoId, ctx, out);

        List<Posicion> tabla = posicionRepo.findByTorneoId(torneoId).stream()
                .filter(p -> pj(p) >= 2)
                .toList();
        if (!tabla.isEmpty()) {
            equipoInvicto(tabla, ctx, out);
            mejorDefensa(tabla, ctx, out);
            maximoGoleadorEquipo(tabla, ctx, out);
        }
        return out;
    }

    private void maximoGoleador(Long torneoId, ContextoNoticia ctx, List<Noticia> out) {
        var stats = ejRepo.findByMetricaAndTorneoIdOrderByValorDesc("goles", torneoId);
        var top = stats.stream().filter(s -> s.getValor() > 0).findFirst().orElse(null);
        if (top == null) return;
        Jugador j = jugadorRepo.findById(top.getJugadorId()).orElse(null);
        if (j == null) return;
        int goles = (int) top.getValor();
        Equipo eq = j.getEquipo();

        Noticia n = base(CategoriaNoticia.RECORD, ctx);
        n.setTitulo(j.getNombre() + " es el máximo goleador del torneo con " + goles + " goles");
        n.setSubtitulo("Bota de Oro provisional" + (eq != null ? " · " + eq.getNombre() : ""));
        n.setResumen(j.getNombre() + " lidera la tabla de goleadores con " + goles
                + " tantos y se perfila como la gran figura ofensiva del Mundial.");
        n.setRelevancia(72);
        n.setImagenTipo("JUGADOR");
        n.setJugadorId(j.getId());
        if (eq != null) n.setEquipoLocalId(eq.getId());
        n.setTags(new ArrayList<>(List.of(j.getNombre(), eq != null ? eq.getNombre() : "—", "Goleador")));
        n.setClaveNatural("RECORD:goleador");
        out.add(n);
    }

    private void equipoInvicto(List<Posicion> tabla, ContextoNoticia ctx, List<Noticia> out) {
        Posicion mejor = tabla.stream()
                .filter(p -> p.getPerdidos() == 0 && pj(p) >= 3)
                .max(Comparator.comparingInt(Posicion::getPuntos))
                .orElse(null);
        if (mejor == null) return;
        Equipo e = equipoRepo.findById(mejor.getEquipoId()).orElse(null);
        if (e == null) return;

        Noticia n = base(CategoriaNoticia.RECORD, ctx);
        n.setTitulo(e.getNombre() + " sigue invicto en el Mundial");
        n.setSubtitulo(mejor.getGanados() + "G " + mejor.getEmpatados() + "E · "
                + mejor.getPuntos() + " puntos");
        n.setResumen(e.getNombre() + " todavía no conoce la derrota: " + mejor.getGanados()
                + " victorias y " + mejor.getEmpatados() + " empates lo mantienen como uno de los equipos a vencer.");
        n.setRelevancia(68);
        n.setImagenTipo("EQUIPO");
        n.setEquipoLocalId(e.getId());
        n.setTags(new ArrayList<>(List.of(e.getNombre(), "Invicto")));
        n.setClaveNatural("RECORD:invicto");
        out.add(n);
    }

    private void mejorDefensa(List<Posicion> tabla, ContextoNoticia ctx, List<Noticia> out) {
        Posicion mejor = tabla.stream()
                .min(Comparator.<Posicion>comparingInt(Posicion::getGolesContra)
                        .thenComparing(Comparator.comparingInt(Posicion::getPuntos).reversed()))
                .orElse(null);
        if (mejor == null) return;
        Equipo e = equipoRepo.findById(mejor.getEquipoId()).orElse(null);
        if (e == null) return;

        Noticia n = base(CategoriaNoticia.RECORD, ctx);
        n.setTitulo(e.getNombre() + " tiene la mejor defensa del torneo");
        n.setSubtitulo("Sólo " + mejor.getGolesContra() + " goles en contra en " + pj(mejor) + " partidos");
        n.setResumen("La solidez defensiva de " + e.getNombre() + " es la más destacada del Mundial: apenas "
                + mejor.getGolesContra() + " goles recibidos en " + pj(mejor) + " encuentros.");
        n.setRelevancia(62);
        n.setImagenTipo("EQUIPO");
        n.setEquipoLocalId(e.getId());
        n.setTags(new ArrayList<>(List.of(e.getNombre(), "Defensa")));
        n.setClaveNatural("RECORD:defensa");
        out.add(n);
    }

    private void maximoGoleadorEquipo(List<Posicion> tabla, ContextoNoticia ctx, List<Noticia> out) {
        Posicion mejor = tabla.stream()
                .max(Comparator.comparingInt(Posicion::getGolesFavor))
                .orElse(null);
        if (mejor == null || mejor.getGolesFavor() == 0) return;
        Equipo e = equipoRepo.findById(mejor.getEquipoId()).orElse(null);
        if (e == null) return;

        Noticia n = base(CategoriaNoticia.RECORD, ctx);
        n.setTitulo(e.getNombre() + " es el equipo más goleador del Mundial");
        n.setSubtitulo(mejor.getGolesFavor() + " goles convertidos en " + pj(mejor) + " partidos");
        n.setResumen("El ataque de " + e.getNombre() + " es el más prolífico del torneo con "
                + mejor.getGolesFavor() + " goles a favor.");
        n.setRelevancia(60);
        n.setImagenTipo("EQUIPO");
        n.setEquipoLocalId(e.getId());
        n.setTags(new ArrayList<>(List.of(e.getNombre(), "Ofensiva")));
        n.setClaveNatural("RECORD:ataque");
        out.add(n);
    }

    private static int pj(Posicion p) {
        return p.getGanados() + p.getEmpatados() + p.getPerdidos();
    }

    private static Noticia base(CategoriaNoticia cat, ContextoNoticia ctx) {
        Noticia n = new Noticia();
        n.setCategoria(cat);
        n.setFecha(LocalDateTime.now());
        n.setOrigen(ctx.origen());
        return n;
    }
}
