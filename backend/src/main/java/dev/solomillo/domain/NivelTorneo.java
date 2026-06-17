package dev.solomillo.domain;

/**
 * Nivel/importancia de un torneo según el sistema de eloratings.net.
 * El valor asociado es el factor K base del cálculo de rating.
 */
public enum NivelTorneo {
    MUNDIAL(60),        // Fase final de Copa del Mundo
    CONTINENTAL(50),    // Finales continentales / intercontinentales mayores
    CLASIFICATORIO(40), // Eliminatorias de Mundial/continentales + torneos mayores
    OTRO(30),           // Resto de torneos
    AMISTOSO(20);       // Amistosos

    private final int k;

    NivelTorneo(int k) {
        this.k = k;
    }

    public int k() {
        return k;
    }
}
