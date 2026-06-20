package dev.solomillo.noticias;

import dev.solomillo.domain.Partido;

/**
 * Contexto que reciben los {@link DetectorNoticia}. Si {@code partido} no es null,
 * el detector trabaja sólo sobre ese partido recién cerrado (modo evento); si es
 * null, opera en modo batch sobre todo el torneo (regeneración global).
 */
public record ContextoNoticia(Long torneoId, Partido partido, String origen) {

    public boolean esBatch() {
        return partido == null;
    }
}
