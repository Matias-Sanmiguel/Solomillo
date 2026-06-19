package dev.solomillo.api;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.events.EventoInterno;
import dev.solomillo.events.MotorProcesamiento;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.PartidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final PartidoRepository partidoRepo;
    private final JugadorRepository jugadorRepo;
    private final MotorProcesamiento motor;
    private final Map<Long, Integer> minutos = new ConcurrentHashMap<>();

    public DemoController(PartidoRepository p, JugadorRepository j, MotorProcesamiento m) {
        this.partidoRepo = p;
        this.jugadorRepo = j;
        this.motor = m;
    }

    @PostMapping("/activar/{partidoId}")
    public Map<String, Object> activar(@PathVariable Long partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        partido.setEstado(EstadoPartido.EN_VIVO);
        partido.setGolesLocal(0);
        partido.setGolesVisitante(0);
        partidoRepo.save(partido);
        minutos.put(partidoId, 1);
        return Map.of("estado", "EN_VIVO", "partido_id", partidoId);
    }

    @PostMapping("/gol/{partidoId}")
    public Map<String, Object> simularGol(@PathVariable Long partidoId,
                                           @RequestParam(defaultValue = "local") String equipo) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (partido.getEstado() != EstadoPartido.EN_VIVO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Partido no está EN_VIVO");

        int minuto = minutos.merge(partidoId, 5, Integer::sum);
        minuto = Math.min(minuto, 90);

        Long equipoId = "local".equals(equipo)
                ? partido.getEquipoLocal().getId()
                : partido.getEquipoVisitante().getId();

        List<Long> jugadores = jugadorRepo.findByEquipoId(equipoId)
                .stream().map(j -> j.getId()).toList();
        Long jugadorId = jugadores.isEmpty() ? null
                : jugadores.get(new Random().nextInt(jugadores.size()));

        if ("local".equals(equipo)) {
            partido.setGolesLocal((partido.getGolesLocal() == null ? 0 : partido.getGolesLocal()) + 1);
        } else {
            partido.setGolesVisitante((partido.getGolesVisitante() == null ? 0 : partido.getGolesVisitante()) + 1);
        }
        partidoRepo.save(partido);

        var evento = new EventoInterno("gol", partidoId, minuto, jugadorId, Map.of("equipo", equipo));
        motor.procesar(evento, "demo");

        return Map.of(
                "partido_id", partidoId,
                "equipo", equipo,
                "minuto", minuto,
                "goles_local", partido.getGolesLocal(),
                "goles_visitante", partido.getGolesVisitante()
        );
    }

    @PostMapping("/tarjeta/{partidoId}")
    public Map<String, Object> simularTarjeta(@PathVariable Long partidoId,
                                               @RequestParam(defaultValue = "amarilla") String color,
                                               @RequestParam(defaultValue = "local") String equipo) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (partido.getEstado() != EstadoPartido.EN_VIVO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Partido no está EN_VIVO");

        int minuto = minutos.getOrDefault(partidoId, 45);
        var evento = new EventoInterno("tarjeta", partidoId, minuto, null,
                Map.of("color", color, "equipo", equipo));
        motor.procesar(evento, "demo");

        return Map.of("partido_id", partidoId, "tarjeta", color, "equipo", equipo, "minuto", minuto);
    }

    @PostMapping("/finalizar/{partidoId}")
    public Map<String, Object> finalizar(@PathVariable Long partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var evento = new EventoInterno("fin_partido", partidoId, 90, null, Map.of());
        motor.procesar(evento, "demo");
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoRepo.save(partido);
        minutos.remove(partidoId);
        return Map.of("estado", "FINALIZADO", "partido_id", partidoId);
    }
}
