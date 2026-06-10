package dev.solomillo.alerts;

import dev.solomillo.events.EventoInterno;
import dev.solomillo.repository.AlertaRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GeneradorAlertas {

    private final AlertaRepository alertaRepo;
    private final List<ReglaAlerta> reglas;

    public GeneradorAlertas(AlertaRepository alertaRepo) {
        this.alertaRepo = alertaRepo;
        this.reglas = List.of(
                e -> "gol".equals(e.tipo()) ? "Gol al minuto " + e.minuto() : null,
                e -> {
                    if (!"tarjeta".equals(e.tipo())) return null;
                    Object color = e.datos().get("color");
                    return "roja".equals(color) ? "Expulsión al minuto " + e.minuto() : null;
                },
                e -> "fin_partido".equals(e.tipo()) ? "Fin del partido" : null
        );
    }

    public List<Map<String, Object>> evaluar(EventoInterno evento) {
        return reglas.stream()
                .map(r -> r.evaluar(evento))
                .filter(Objects::nonNull)
                .map(msg -> {
                    var a = new Alerta();
                    a.setPartidoId(evento.partidoId());
                    a.setTipo(evento.tipo());
                    a.setMensaje(msg);
                    alertaRepo.save(a);
                    Map<String, Object> m = new HashMap<>();
                    m.put("partido_id", a.getPartidoId());
                    m.put("tipo", a.getTipo());
                    m.put("mensaje", a.getMensaje());
                    return m;
                })
                .toList();
    }
}
