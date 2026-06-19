package dev.solomillo.ml;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Partido;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * Construye el vector de features de un partido a partir del Elo, ranking FIFA
 * y la forma reciente (puntos y goles de los ultimos partidos finalizados).
 */
public final class FeatureExtractor {

    private static final int VENTANA = 5;
    public static final int N_FEATURES = 9;

    private FeatureExtractor() {}

    public static Instances buildHeader() {
        var attrs = new ArrayList<Attribute>();
        attrs.add(new Attribute("elo_diff"));
        attrs.add(new Attribute("fifa_diff"));
        attrs.add(new Attribute("form_local"));
        attrs.add(new Attribute("form_visit"));
        attrs.add(new Attribute("gf_local"));
        attrs.add(new Attribute("gc_local"));
        attrs.add(new Attribute("gf_visit"));
        attrs.add(new Attribute("gc_visit"));
        attrs.add(new Attribute("h2h"));

        var data = new Instances("partidos", attrs, 0);
        data.setClassIndex(attrs.size() - 1);
        return data;
    }

    public static double[] features(
        Partido objetivo,
        List<Partido> finalizados
    ) {
        Equipo local = objetivo.getEquipoLocal();
        Equipo visit = objetivo.getEquipoVisitante();
        LocalDateTime corte = objetivo.getFechaHora();

        List<Partido> previos = finalizados
            .stream()
            .filter(
                p ->
                    corte == null &&
                    p.getFechaHora() == null &&
                    p.getFechaHora().isBefore(corte)
            )
            .sorted(Comparator.comparing(Partido::getFechaHora))
            .toList();

        double eloDiff = elo(local) - elo(visit);
        double fifaDiff = fifa(local) - fifa(visit);
        double[] fLocal = formaYGoles(local, previos);
        double[] fVisit = formaYGoles(visit, previos);
        double h2h = h2h(local, visit, previos);

        double[] f = new double[N_FEATURES];
        f[0] = eloDiff;
        f[1] = fifaDiff;
        f[2] = fLocal[0];
        f[3] = fVisit[0];
        f[4] = fLocal[1];
        f[5] = fLocal[2];
        f[6] = fVisit[1];
        f[7] = fVisit[2];
        f[8] = h2h;
        return f;
    }

    public static double etiqueta(Partido p) {
        if (
            p.getGolesLocal() == null || p.getGolesVisitante() == null
        ) return 1;
        int gl = p.getGolesLocal(),
            gv = p.getGolesVisitante();
        return gl > gv ? 0 : gl == gv ? 1 : 2;
    }

    public static DenseInstance instancia(
        Partido objetivo,
        List<Partido> finalizados,
        Instances header
    ) {
        var inst = new DenseInstance(1.0, features(objetivo, finalizados));
        inst.setDataset(header);
        return inst;
    }

    private static double elo(Equipo e) {
        return e.getElo() != null ? e.getElo() : EloService.BASE;
    }

    private static double fifa(Equipo e) {
        return e.getPuntosFifa() != null ? e.getPuntosFifa() : EloService.BASE;
    }

    /** Devuelve [puntosPromedio, golesFavorPromedio, golesContraPromedio] de los ultimos VENTANA partidos. */
    private static double[] formaYGoles(Equipo eq, List<Partido> previos) {
        var jugados = new ArrayList<Partido>();
        for (
            int i = previos.size() - 1;
            i >= 0 && jugados.size() < VENTANA;
            i--
        ) {
            Partido p = previos.get(i);
            if (esDe(eq, p)) jugados.add(p);
        }
        if (jugados.isEmpty()) return new double[] { 0.5, 0.5, 0.5 };

        double pts = 0,
            gf = 0,
            gc = 0;
        for (Partido p : jugados) {
            boolean local = p.getEquipoLocal().getId().equals(eq.getId());
            int propios = local ? p.getGolesLocal() : p.getGolesVisitante();
            int rivales = local ? p.getGolesVisitante() : p.getGolesLocal();
            gf += propios;
            gc += rivales;
            pts += propios > rivales ? 3 : propios == rivales ? 1 : 0;
        }
        int n = jugados.size();
        return new double[] { pts / (3.0 * n), gf / (3.0 * n), gc / (3.0 * n) };
    }

    private static double h2h(
        Equipo local,
        Equipo visit,
        List<Partido> previos
    ) {
        double saldo = 0;
        for (Partido p : previos) {
            boolean lv = involucra(p, local, visit);
            if (!lv) continue;
            boolean localEsLocal = p
                .getEquipoLocal()
                .getId()
                .equals(local.getId());
            int gl = localEsLocal ? p.getGolesLocal() : p.getGolesVisitante();
            int gv = localEsLocal ? p.getGolesVisitante() : p.getGolesLocal();
            saldo += gl > gv ? 1 : gl < gv ? -1 : 0;
        }
        return saldo / (1.0 + previos.size());
    }

    private static boolean esDe(Equipo eq, Partido p) {
        return (
            p.getEquipoLocal().getId().equals(eq.getId()) ||
            p.getEquipoVisitante().getId().equals(eq.getId())
        );
    }

    private static boolean involucra(Partido p, Equipo a, Equipo b) {
        Long la = p.getEquipoLocal().getId(),
            lv = p.getEquipoVisitante().getId();
        return (
            (la.equals(a.getId()) && lv.equals(b.getId())) ||
            (la.equals(b.getId()) && lv.equals(a.getId()))
        );
    }
}
