package dev.solomillo.alerts;

import dev.solomillo.events.EventoInterno;

public interface ReglaAlerta {
    String evaluar(EventoInterno evento);
}
