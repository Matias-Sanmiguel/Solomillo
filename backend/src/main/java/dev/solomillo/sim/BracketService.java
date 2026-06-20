package dev.solomillo.sim;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Ronda;
import dev.solomillo.domain.Torneo;
import dev.solomillo.rankings.Posicion;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PosicionRepository;
import dev.solomillo.repository.TorneoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Avance del cuadro de eliminatorias del Mundial 2026. El fixture sembrado deja los partidos de
 * llave con equipos {@code null} (TBD); este servicio los completa cuando la ronda alimentadora
 * termina:
 * <ul>
 *   <li>Cerrada la fase de grupos → siembra los 32 clasificados (1.º y 2.º de cada grupo + 8
 *       mejores terceros) en los dieciseisavos.</li>
 *   <li>Cerrada una ronda de llave → los ganadores avanzan a la siguiente (y los perdedores de
 *       semifinal van al partido por el tercer puesto).</li>
 * </ul>
 * El emparejado es secuencial y determinista (documentado): el mapa oficial de cruces del Mundial
 * 2026 no está en el fixture, y para un simulador la coherencia ronda-a-ronda es lo relevante.
 */
@Service
public class BracketService {

    private static final String TORNEO_MUNDIAL = "Copa Mundial FIFA 2026";
    private static final String TEMPORADA = "2026";

    private final TorneoRepository torneoRepo;
    private final PartidoRepository partidoRepo;
    private final PosicionRepository posicionRepo;
    private final EquipoRepository equipoRepo;

    public BracketService(TorneoRepository torneoRepo, PartidoRepository partidoRepo,
                          PosicionRepository posicionRepo, EquipoRepository equipoRepo) {
        this.torneoRepo = torneoRepo;
        this.partidoRepo = partidoRepo;
        this.posicionRepo = posicionRepo;
        this.equipoRepo = equipoRepo;
    }

    private Optional<Torneo> mundial() {
        return torneoRepo.findByNombreAndTemporada(TORNEO_MUNDIAL, TEMPORADA);
    }

    /**
     * Completa todos los cruces de llave cuya ronda alimentadora ya esté cerrada. Devuelve la
     * cantidad de partidos a los que se les asignó rival. Es idempotente: no toca cruces ya
     * definidos ni rondas alimentadoras incompletas.
     */
    @Transactional
    public int avanzarCuadro() {
        Torneo m = mundial().orElse(null);
        if (m == null) return 0;
        Long id = m.getId();
        int cambios = 0;

        cambios += sembrarDieciseisavos(id);
        cambios += avanzarRonda(id, Ronda.DIECISEISAVOS, Ronda.OCTAVOS);
        cambios += avanzarRonda(id, Ronda.OCTAVOS, Ronda.CUARTOS);
        cambios += avanzarRonda(id, Ronda.CUARTOS, Ronda.SEMIFINAL);
        cambios += avanzarFinales(id);
        return cambios;
    }

    /** True si en la fase de grupos no queda ningún partido sin finalizar. */
    public boolean gruposCompletos(Long torneoId) {
        return partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, Ronda.GRUPOS)
                .stream().allMatch(p -> p.getEstado() == EstadoPartido.FINALIZADO);
    }

    private int sembrarDieciseisavos(Long torneoId) {
        List<Partido> dieciseisavos = partidoRepo
                .findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, Ronda.DIECISEISAVOS);
        // Ya sembrados o grupos sin terminar: nada que hacer.
        if (dieciseisavos.isEmpty() || dieciseisavos.get(0).getEquipoLocal() != null) return 0;
        if (!gruposCompletos(torneoId)) return 0;

        List<Equipo> clasificados = clasificados(torneoId);
        if (clasificados.size() < dieciseisavos.size() * 2) return 0;

        return asignar(dieciseisavos, clasificados);
    }

    /**
     * Clasificados a dieciseisavos: 1.º y 2.º de cada grupo + 8 mejores terceros. Orden de siembra:
     * ganadores de grupo (A→L), luego mejores terceros, luego segundos (L→A). Así se evita que el
     * 1.º y el 2.º de un mismo grupo se crucen en la primera ronda.
     */
    private List<Equipo> clasificados(Long torneoId) {
        Map<String, List<Posicion>> porGrupo = new TreeMap<>();
        for (Posicion pos : posicionRepo.findByTorneoId(torneoId)) {
            Equipo e = equipoRepo.findById(pos.getEquipoId()).orElse(null);
            if (e == null || e.getGrupo() == null) continue;
            porGrupo.computeIfAbsent(e.getGrupo(), k -> new ArrayList<>()).add(pos);
        }

        List<Equipo> ganadores = new ArrayList<>();
        List<Equipo> segundos = new ArrayList<>();
        List<Posicion> terceros = new ArrayList<>();
        for (List<Posicion> grupo : porGrupo.values()) {
            grupo.sort(COMPARADOR);
            if (grupo.size() > 0) ganadores.add(equipoRepo.findById(grupo.get(0).getEquipoId()).orElse(null));
            if (grupo.size() > 1) segundos.add(equipoRepo.findById(grupo.get(1).getEquipoId()).orElse(null));
            if (grupo.size() > 2) terceros.add(grupo.get(2));
        }
        terceros.sort(COMPARADOR);
        List<Equipo> mejoresTerceros = terceros.stream().limit(8)
                .map(p -> equipoRepo.findById(p.getEquipoId()).orElse(null)).toList();

        List<Equipo> orden = new ArrayList<>(ganadores);
        orden.addAll(mejoresTerceros);
        List<Equipo> segundosRev = new ArrayList<>(segundos);
        java.util.Collections.reverse(segundosRev);
        orden.addAll(segundosRev);
        orden.removeIf(java.util.Objects::isNull);
        return orden;
    }

    /** Tabla por puntos, luego diferencia de gol, luego goles a favor (misma regla que /clasificacion). */
    private static final Comparator<Posicion> COMPARADOR = (a, b) -> {
        int c = Integer.compare(b.getPuntos(), a.getPuntos());
        if (c != 0) return c;
        c = Integer.compare(b.getGolesFavor() - b.getGolesContra(), a.getGolesFavor() - a.getGolesContra());
        if (c != 0) return c;
        return Integer.compare(b.getGolesFavor(), a.getGolesFavor());
    };

    /** Avanza los ganadores de {@code from} (en orden) a los cruces de {@code to}. */
    private int avanzarRonda(Long torneoId, Ronda from, Ronda to) {
        List<Partido> origen = partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, from);
        List<Partido> destino = partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, to);
        if (origen.isEmpty() || destino.isEmpty()) return 0;
        if (destino.get(0).getEquipoLocal() != null) return 0;            // ya sembrada
        if (!origen.stream().allMatch(p -> p.getEstado() == EstadoPartido.FINALIZADO)) return 0;

        List<Equipo> ganadores = origen.stream().map(this::ganador).toList();
        return asignar(destino, ganadores);
    }

    /** Semifinal cerrada → ganadores a la FINAL y perdedores al TERCER_PUESTO. */
    private int avanzarFinales(Long torneoId) {
        List<Partido> semis = partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, Ronda.SEMIFINAL);
        if (semis.size() < 2) return 0;
        if (!semis.stream().allMatch(p -> p.getEstado() == EstadoPartido.FINALIZADO)) return 0;

        int cambios = 0;
        List<Partido> finales = partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, Ronda.FINAL);
        if (!finales.isEmpty() && finales.get(0).getEquipoLocal() == null) {
            cambios += asignar(finales, List.of(ganador(semis.get(0)), ganador(semis.get(1))));
        }
        List<Partido> tercero = partidoRepo.findByTorneo_IdAndRondaOrderByFechaHoraAsc(torneoId, Ronda.TERCER_PUESTO);
        if (!tercero.isEmpty() && tercero.get(0).getEquipoLocal() == null) {
            cambios += asignar(tercero, List.of(perdedor(semis.get(0)), perdedor(semis.get(1))));
        }
        return cambios;
    }

    /** Empareja consecutivamente: cruce i ← equipos[2i] (local) vs equipos[2i+1] (visitante). */
    private int asignar(List<Partido> cruces, List<Equipo> equipos) {
        int cambios = 0;
        for (int i = 0; i < cruces.size() && (2 * i + 1) < equipos.size(); i++) {
            Partido p = cruces.get(i);
            p.setEquipoLocal(equipos.get(2 * i));
            p.setEquipoVisitante(equipos.get(2 * i + 1));
            partidoRepo.save(p);
            cambios++;
        }
        return cambios;
    }

    private Equipo ganador(Partido p) {
        int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
        int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();
        return gl >= gv ? p.getEquipoLocal() : p.getEquipoVisitante();
    }

    private Equipo perdedor(Partido p) {
        int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
        int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();
        return gl >= gv ? p.getEquipoVisitante() : p.getEquipoLocal();
    }
}
