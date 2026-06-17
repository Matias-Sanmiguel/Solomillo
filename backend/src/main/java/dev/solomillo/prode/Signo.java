package dev.solomillo.prode;

public enum Signo {
    LOCAL,
    EMPATE,
    VISITANTE;

    public static Signo desde(Integer golesLocal, Integer golesVisitante) {
        if (golesLocal == null || golesVisitante == null) return null;
        if (golesLocal > golesVisitante) return LOCAL;
        if (golesLocal < golesVisitante) return VISITANTE;
        return EMPATE;
    }
}
