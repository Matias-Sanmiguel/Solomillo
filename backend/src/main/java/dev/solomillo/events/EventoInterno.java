package dev.solomillo.events;

import java.util.Map;

public record EventoInterno(
        String tipo,
        Long partidoId,
        int minuto,
        Long jugadorId,
        Map<String, Object> datos
) {}
