package dev.solomillo.ml;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.NivelTorneo;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.repository.EloHistorialRepository;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EloService {

    // Ventaja de local de eloratings.net: +100 puntos a la diferencia de rating
    private static final double HOME_ADV = 100.0;
    public static final double BASE = 1500.0;

    private final EquipoRepository equipoRepo;
    private final PartidoRepository partidoRepo;
    private final EloHistorialRepository historialRepo;

    public EloService(EquipoRepository e, PartidoRepository p, EloHistorialRepository h) {
        this.equipoRepo = e;
        this.partidoRepo = p;
        this.historialRepo = h;
    }

    public static double baseElo(Equipo eq) {
        return eq.getElo() != null ? eq.getElo() : BASE;
    }

    // Wₑ = 1 / (10^(−dr/400) + 1), con dr = (propio − rival) + ventaja de local
    static double esperado(double propio, double rival, double ventaja) {
        return 1.0 / (1.0 + Math.pow(10, (rival - propio - ventaja) / 400.0));
    }

    /** Factor K base según la importancia del torneo (tabla de eloratings.net). */
    static int pesoTorneo(Torneo torneo) {
        NivelTorneo nivel = torneo != null && torneo.getNivel() != null
                ? torneo.getNivel() : NivelTorneo.OTRO;
        return nivel.k();
    }

    /** Ajuste de K por diferencia de goles (tabla de eloratings.net). */
    static double multiplicadorGoles(int diferencia) {
        int dif = Math.abs(diferencia);
        if (dif <= 1) return 1.0;
        if (dif == 2) return 1.5;
        if (dif == 3) return 1.75;
        return 1.75 + (dif - 3) / 8.0;
    }

    public void aplicarResultado(Partido p) {
        if (p.getGolesLocal() == null || p.getGolesVisitante() == null) return;
        Equipo local = p.getEquipoLocal();
        Equipo visitante = p.getEquipoVisitante();
        double eLocal = baseElo(local);
        double eVisit = baseElo(visitante);

        double scoreLocal = p.getGolesLocal() > p.getGolesVisitante() ? 1.0
                : p.getGolesLocal().equals(p.getGolesVisitante()) ? 0.5 : 0.0;

        double ventaja = p.isNeutral() ? 0.0 : HOME_ADV;
        double k = pesoTorneo(p.getTorneo())
                * multiplicadorGoles(p.getGolesLocal() - p.getGolesVisitante());

        double expLocal = esperado(eLocal, eVisit, ventaja);
        double delta = k * (scoreLocal - expLocal);

        local.setElo(eLocal + delta);
        visitante.setElo(eVisit - delta);
        equipoRepo.save(local);
        equipoRepo.save(visitante);

        LocalDateTime fecha = p.getFechaHora() != null ? p.getFechaHora() : LocalDateTime.now();
        historialRepo.save(new EloHistorial(local, local.getElo(), fecha));
        historialRepo.save(new EloHistorial(visitante, visitante.getElo(), fecha));
    }

    @Transactional
    public void recalcularTodo() {
        historialRepo.deleteAll();
        equipoRepo.findAll().forEach(e -> {
            e.setElo(e.getPuntosFifa() != null ? fifaPuntosAElo(e.getPuntosFifa()) : BASE);
            equipoRepo.save(e);
        });
        partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.FINALIZADO).forEach(this::aplicarResultado);
    }

    public static double fifaPuntosAElo(int rankingFifa) {
        return rankingFifa;
    }
}
