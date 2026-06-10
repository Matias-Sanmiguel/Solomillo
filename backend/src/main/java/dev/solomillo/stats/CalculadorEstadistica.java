package dev.solomillo.stats;

import dev.solomillo.events.EventoInterno;

public interface CalculadorEstadistica {
    boolean aplica(EventoInterno evento);
    void actualizar(EventoInterno evento);
}
