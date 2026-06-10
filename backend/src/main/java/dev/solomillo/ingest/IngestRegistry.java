package dev.solomillo.ingest;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class IngestRegistry {

    private final Map<String, FuenteAdapter> adapters;

    public IngestRegistry(Map<String, FuenteAdapter> adapters) {
        this.adapters = adapters;
    }

    public FuenteAdapter get(String nombre) {
        FuenteAdapter adapter = adapters.get(nombre);
        if (adapter == null) throw new IllegalArgumentException("Fuente no registrada: " + nombre);
        return adapter;
    }
}
