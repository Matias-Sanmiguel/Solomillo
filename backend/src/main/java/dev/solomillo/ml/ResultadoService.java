package dev.solomillo.ml;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PrediccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultadoService {

    private final PartidoRepository partidoRepo;
    private final PrediccionRepository prediccionRepo;
    private final EloService eloService;

    public ResultadoService(PartidoRepository p, PrediccionRepository pr, EloService e) {
        this.partidoRepo = p;
        this.prediccionRepo = pr;
        this.eloService = e;
    }

    @Transactional
    public Partido registrar(Long partidoId, int golesLocal, int golesVisitante) {
        Partido p = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new IllegalArgumentException("Partido inexistente"));
        p.setGolesLocal(golesLocal);
        p.setGolesVisitante(golesVisitante);
        p.setEstado(EstadoPartido.FINALIZADO);
        partidoRepo.save(p);

        eloService.aplicarResultado(p);

        int real = (int) FeatureExtractor.etiqueta(p);
        prediccionRepo.findByPartidoIdAndResultadoRealIsNull(partidoId).forEach(pred -> {
            pred.setResultadoReal(real);
            prediccionRepo.save(pred);
        });
        return p;
    }
}
