package dev.solomillo.prode;

import dev.solomillo.domain.Partido;

/**
 * Strategy de puntuación del prode. Permite versionar/cambiar el esquema de
 * puntos sin tocar el resolutor (mismo principio que CalculadorEstadistica
 * y ReglaAlerta).
 */
public interface PolizaPuntaje {

    /** Puntos que otorga un pronóstico frente al resultado real del partido. */
    int calcular(Pronostico pronostico, Partido partido);

    /** Identificador de la política (auditoría / trazabilidad). */
    String version();
}
