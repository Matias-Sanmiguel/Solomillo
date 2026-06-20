package dev.solomillo.noticias;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Genera el feed inicial de noticias al arranque, después de los seeds
 * (DataLoader @Order 1, FifaEloSeedService @Order 2). Hace backfill de
 * predicciones (si hay modelo) y regenera todas las noticias. Tolerante a
 * fallos: nunca impide el arranque de la aplicación.
 */
@Component
@Order(3)
public class NoticiasBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NoticiasBootstrap.class);

    private final PrediccionBackfill backfill;
    private final NoticiaService noticiaService;

    public NoticiasBootstrap(PrediccionBackfill backfill, NoticiaService noticiaService) {
        this.backfill = backfill;
        this.noticiaService = noticiaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int preds = backfill.backfillMundial();
            int noticias = noticiaService.regenerarTodo();
            log.info("Feed de noticias inicial: {} predicciones backfilled, {} noticias generadas", preds, noticias);
        } catch (Exception e) {
            log.warn("No se pudo generar el feed de noticias inicial: {}", e.getMessage());
        }
    }
}
