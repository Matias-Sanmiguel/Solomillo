package dev.solomillo.distribution;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Publisher {

    private final List<CanalDistribucion> canales;

    public Publisher(List<CanalDistribucion> canales) {
        this.canales = canales;
    }

    public void publicar(String topico, Object mensaje) {
        canales.forEach(c -> c.enviar(topico, mensaje));
    }
}
