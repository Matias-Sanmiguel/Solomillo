package dev.solomillo.prode;

import dev.solomillo.distribution.Publisher;
import dev.solomillo.domain.Partido;
import dev.solomillo.repository.PronosticoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Resuelve los pronósticos pendientes de un partido finalizado: calcula los
 * puntos con la {@link PolizaPuntaje} activa, actualiza el ranking y notifica.
 * Idempotente: sólo procesa pronósticos con puntos == null.
 */
@Service
public class ResolutorProde {

    private final PronosticoRepository pronosticoRepo;
    private final PolizaPuntaje poliza;
    private final RankingProdeService ranking;
    private final Publisher publisher;

    public ResolutorProde(PronosticoRepository pronosticoRepo, PolizaPuntaje poliza,
                          RankingProdeService ranking, Publisher publisher) {
        this.pronosticoRepo = pronosticoRepo;
        this.poliza = poliza;
        this.ranking = ranking;
        this.publisher = publisher;
    }

    @Transactional
    public void resolver(Partido partido) {
        if (partido.getGolesLocal() == null || partido.getGolesVisitante() == null) return;

        Long torneoId = partido.getTorneo() != null ? partido.getTorneo().getId() : null;

        for (Pronostico p : pronosticoRepo.findByPartidoIdAndPuntosIsNull(partido.getId())) {
            int puntos = poliza.calcular(p, partido);
            p.setPuntos(puntos);
            pronosticoRepo.save(p);

            ranking.registrar(p.getUsuario(), torneoId, puntos, puntos > 0);

            publisher.publicar("prode", Map.of(
                    "tipo", "resolucion",
                    "usuario_id", p.getUsuario().getId(),
                    "partido_id", partido.getId(),
                    "puntos", puntos
            ));
        }
    }
}
