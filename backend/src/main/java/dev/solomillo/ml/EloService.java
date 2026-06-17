package dev.solomillo.ml;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.repository.EloHistorialRepository;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EloService {

    private static final double K = 32.0;
    private static final double HOME_ADV = 65.0;
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

    private static double esperado(double propio, double rival, double ventaja) {
        return 1.0 / (1.0 + Math.pow(10, (rival - propio - ventaja) / 400.0));
    }

    public void aplicarResultado(Partido p) {
        if (p.getGolesLocal() == null || p.getGolesVisitante() == null) return;
        Equipo local = p.getEquipoLocal();
        Equipo visitante = p.getEquipoVisitante();
        double eLocal = baseElo(local);
        double eVisit = baseElo(visitante);

        double scoreLocal = p.getGolesLocal() > p.getGolesVisitante() ? 1.0
                : p.getGolesLocal().equals(p.getGolesVisitante()) ? 0.5 : 0.0;
        double margen = Math.max(1.0, Math.abs(p.getGolesLocal() - p.getGolesVisitante()));
        double mult = Math.sqrt(margen);

        double expLocal = esperado(eLocal, eVisit, HOME_ADV);
        double delta = K * mult * (scoreLocal - expLocal);

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
