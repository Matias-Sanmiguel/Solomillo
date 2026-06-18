package dev.solomillo.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Segundo canal de distribución (patrón Strategy/Observer): publica los eventos como
 * líneas de reporte legibles. Representa el canal de "reportes" del enunciado, distinto
 * del canal en tiempo real (Redis → WebSocket).
 *
 * <p>Al implementar {@link CanalDistribucion} y ser un {@code @Component}, Spring lo
 * inyecta automáticamente en la lista que recorre {@link Publisher}: se suma un nuevo
 * destino de publicación sin modificar el publisher ni el motor.
 */
@Component
public class ReporteChannel implements CanalDistribucion {

    private static final Logger log = LoggerFactory.getLogger("REPORTE");

    @Override
    public void enviar(String topico, Object mensaje) {
        log.info("[{}] {}", topico, mensaje);
    }
}
