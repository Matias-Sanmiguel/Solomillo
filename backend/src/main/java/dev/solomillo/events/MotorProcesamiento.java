package dev.solomillo.events;

import dev.solomillo.alerts.GeneradorAlertas;
import dev.solomillo.distribution.Publisher;
import dev.solomillo.rankings.RankingsService;
import dev.solomillo.repository.EventoDeportivoRepository;
import dev.solomillo.stats.CalculadorEstadistica;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class MotorProcesamiento {

    private final EventoDeportivoRepository eventoRepo;
    private final List<CalculadorEstadistica> calculadores;
    private final RankingsService rankings;
    private final GeneradorAlertas alertas;
    private final Publisher publisher;

    public MotorProcesamiento(EventoDeportivoRepository eventoRepo,
                               List<CalculadorEstadistica> calculadores,
                               RankingsService rankings,
                               GeneradorAlertas alertas,
                               Publisher publisher) {
        this.eventoRepo = eventoRepo;
        this.calculadores = calculadores;
        this.rankings = rankings;
        this.alertas = alertas;
        this.publisher = publisher;
    }

    @Transactional
    public void procesar(EventoInterno evento, String fuente) {
        var ed = new EventoDeportivo();
        ed.setPartidoId(evento.partidoId());
        ed.setJugadorId(evento.jugadorId());
        ed.setTipo(evento.tipo());
        ed.setMinuto(evento.minuto());
        ed.setFuente(fuente);
        eventoRepo.save(ed);

        calculadores.stream().filter(c -> c.aplica(evento)).forEach(c -> c.actualizar(evento));
        rankings.actualizar(evento);

        List<Map<String, Object>> alertasList = alertas.evaluar(evento);

        publisher.publicar("evento", Map.of(
                "tipo", evento.tipo(),
                "partido_id", evento.partidoId(),
                "minuto", evento.minuto(),
                "jugador_id", evento.jugadorId() != null ? evento.jugadorId() : ""
        ));
        alertasList.forEach(a -> publisher.publicar("alerta", a));
    }
}
