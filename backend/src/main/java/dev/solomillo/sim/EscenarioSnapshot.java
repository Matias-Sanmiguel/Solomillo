package dev.solomillo.sim;

import java.util.List;

/**
 * Estado serializable del torneo en un instante. Las tablas que sólo crecen (eventos, historial de
 * Elo, estadísticas, posiciones, predicciones) se restauran borrando las filas con id mayor al
 * máximo capturado; las que se modifican in situ se restauran reponiendo sus campos mutables.
 */
public record EscenarioSnapshot(
        long maxEventoId,
        long maxEloHistId,
        long maxEstJugId,
        long maxEstEquipoId,
        long maxPosicionId,
        long maxPrediccionId,
        List<PartidoSnap> partidos,
        List<PosicionSnap> posiciones,
        List<EquipoSnap> equipos,
        List<EstSnap> estJugador,
        List<EstSnap> estEquipo,
        List<PrediccionSnap> predicciones,
        List<ModeloSnap> modelos
) {
    public record PartidoSnap(long id, Long localId, Long visitId, Integer gl, Integer gv,
                              String estado, String ronda) {}
    public record PosicionSnap(long id, int puntos, int ganados, int empatados, int perdidos,
                               int golesFavor, int golesContra) {}
    public record EquipoSnap(long id, Double elo) {}
    public record EstSnap(long id, double valor) {}
    public record PrediccionSnap(long id, Integer resultadoReal) {}
    public record ModeloSnap(long id, boolean activo) {}
}
