package dev.solomillo.ingest;

import dev.solomillo.events.EventoInterno;
import java.util.Map;

public interface FuenteAdapter {
    EventoInterno normalizar(Map<String, Object> payload);
}
