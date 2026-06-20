package dev.solomillo.noticias;

import java.util.List;

/**
 * Estrategia de detección de noticias. Cada categoría de noticia es un
 * {@code @Component} que implementa esta interfaz; Spring los inyecta como
 * {@code List<DetectorNoticia>} en {@link NoticiaService}, igual que el sistema
 * hace con {@code List<CalculadorEstadistica>} en el motor de eventos.
 *
 * Devuelve noticias "candidatas" (sin persistir): el {@link NoticiaService} las
 * hace upsert por clave natural para mantener el feed idempotente.
 */
public interface DetectorNoticia {
    List<Noticia> detectar(ContextoNoticia ctx);
}
