package dev.solomillo.sim;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Ronda;
import dev.solomillo.ml.ModeloPredictivo;
import dev.solomillo.ml.Prediccion;
import dev.solomillo.rankings.Posicion;
import dev.solomillo.repository.*;
import dev.solomillo.stats.EstadisticaEquipo;
import dev.solomillo.stats.EstadisticaJugador;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Crea, restaura y elimina escenarios: snapshots del estado simulable del torneo. Captura los
 * campos mutables de las tablas que la simulación toca y, al restaurar, repone esos campos y borra
 * las filas creadas después del snapshot (eventos, historial de Elo, estadísticas, predicciones).
 */
@Service
public class EscenarioService {

    private final EscenarioRepository escenarioRepo;
    private final PartidoRepository partidoRepo;
    private final PosicionRepository posicionRepo;
    private final EquipoRepository equipoRepo;
    private final EstadisticaJugadorRepository ejRepo;
    private final EstadisticaEquipoRepository eeRepo;
    private final EventoDeportivoRepository eventoRepo;
    private final EloHistorialRepository eloHistRepo;
    private final PrediccionRepository prediccionRepo;
    private final ModeloPredictivoRepository modeloRepo;
    private final ObjectMapper mapper;

    public EscenarioService(EscenarioRepository escenarioRepo, PartidoRepository partidoRepo,
                            PosicionRepository posicionRepo, EquipoRepository equipoRepo,
                            EstadisticaJugadorRepository ejRepo, EstadisticaEquipoRepository eeRepo,
                            EventoDeportivoRepository eventoRepo, EloHistorialRepository eloHistRepo,
                            PrediccionRepository prediccionRepo, ModeloPredictivoRepository modeloRepo,
                            ObjectMapper mapper) {
        this.escenarioRepo = escenarioRepo;
        this.partidoRepo = partidoRepo;
        this.posicionRepo = posicionRepo;
        this.equipoRepo = equipoRepo;
        this.ejRepo = ejRepo;
        this.eeRepo = eeRepo;
        this.eventoRepo = eventoRepo;
        this.eloHistRepo = eloHistRepo;
        this.prediccionRepo = prediccionRepo;
        this.modeloRepo = modeloRepo;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> crear(String nombre, String descripcion) {
        EscenarioSnapshot snap = capturar();
        Escenario e = new Escenario();
        e.setNombre(nombre != null && !nombre.isBlank() ? nombre : "Escenario");
        e.setDescripcion(descripcion);
        try {
            e.setSnapshot(mapper.writeValueAsString(snap));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el escenario: " + ex.getMessage());
        }
        escenarioRepo.save(e);
        return resumen(e);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return escenarioRepo.findAllByOrderByCreadoEnDesc().stream().map(this::resumen).toList();
    }

    @Transactional
    public Map<String, Object> restaurar(Long id) {
        Escenario e = escenarioRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Escenario inexistente"));
        EscenarioSnapshot snap;
        try {
            snap = mapper.readValue(e.getSnapshot(), EscenarioSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Snapshot corrupto: " + ex.getMessage());
        }
        aplicar(snap);
        return Map.of("id", e.getId(), "nombre", e.getNombre(), "restaurado", true);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!escenarioRepo.existsById(id)) {
            throw new IllegalArgumentException("Escenario inexistente");
        }
        escenarioRepo.deleteById(id);
    }

    // --- Captura ---

    private EscenarioSnapshot capturar() {
        List<EscenarioSnapshot.PartidoSnap> partidos = partidoRepo.findAll().stream()
                .map(p -> new EscenarioSnapshot.PartidoSnap(
                        p.getId(),
                        p.getEquipoLocal() != null ? p.getEquipoLocal().getId() : null,
                        p.getEquipoVisitante() != null ? p.getEquipoVisitante().getId() : null,
                        p.getGolesLocal(), p.getGolesVisitante(),
                        p.getEstado() != null ? p.getEstado().name() : null,
                        p.getRonda() != null ? p.getRonda().name() : null))
                .toList();
        List<EscenarioSnapshot.PosicionSnap> posiciones = posicionRepo.findAll().stream()
                .map(x -> new EscenarioSnapshot.PosicionSnap(x.getId(), x.getPuntos(), x.getGanados(),
                        x.getEmpatados(), x.getPerdidos(), x.getGolesFavor(), x.getGolesContra()))
                .toList();
        List<EscenarioSnapshot.EquipoSnap> equipos = equipoRepo.findAll().stream()
                .map(x -> new EscenarioSnapshot.EquipoSnap(x.getId(), x.getElo())).toList();
        List<EscenarioSnapshot.EstSnap> estJug = ejRepo.findAll().stream()
                .map(x -> new EscenarioSnapshot.EstSnap(x.getId(), x.getValor())).toList();
        List<EscenarioSnapshot.EstSnap> estEq = eeRepo.findAll().stream()
                .map(x -> new EscenarioSnapshot.EstSnap(x.getId(), x.getValor())).toList();
        List<EscenarioSnapshot.PrediccionSnap> preds = prediccionRepo.findAll().stream()
                .map(x -> new EscenarioSnapshot.PrediccionSnap(x.getId(), x.getResultadoReal())).toList();
        List<EscenarioSnapshot.ModeloSnap> modelos = modeloRepo.findAll().stream()
                .map(x -> new EscenarioSnapshot.ModeloSnap(x.getId(), x.isActivo())).toList();

        return new EscenarioSnapshot(
                maxId(eventoRepo.findAll().stream().map(x -> x.getId())),
                maxId(eloHistRepo.findAll().stream().map(x -> x.getId())),
                maxId(ejRepo.findAll().stream().map(EstadisticaJugador::getId)),
                maxId(eeRepo.findAll().stream().map(EstadisticaEquipo::getId)),
                maxId(posicionRepo.findAll().stream().map(Posicion::getId)),
                maxId(prediccionRepo.findAll().stream().map(Prediccion::getId)),
                partidos, posiciones, equipos, estJug, estEq, preds, modelos);
    }

    private long maxId(java.util.stream.Stream<Long> ids) {
        return ids.filter(java.util.Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L);
    }

    // --- Restauración ---

    private void aplicar(EscenarioSnapshot snap) {
        // 1) Borrar filas append-only creadas después del snapshot.
        eventoRepo.deleteAll(eventoRepo.findAll().stream()
                .filter(x -> x.getId() > snap.maxEventoId()).toList());
        eloHistRepo.deleteAll(eloHistRepo.findAll().stream()
                .filter(x -> x.getId() > snap.maxEloHistId()).toList());
        ejRepo.deleteAll(ejRepo.findAll().stream()
                .filter(x -> x.getId() > snap.maxEstJugId()).toList());
        eeRepo.deleteAll(eeRepo.findAll().stream()
                .filter(x -> x.getId() > snap.maxEstEquipoId()).toList());
        posicionRepo.deleteAll(posicionRepo.findAll().stream()
                .filter(x -> x.getId() > snap.maxPosicionId()).toList());
        prediccionRepo.deleteAll(prediccionRepo.findAll().stream()
                .filter(x -> x.getId() > snap.maxPrediccionId()).toList());

        // 2) Reponer campos mutables de las filas preexistentes.
        Map<Long, EscenarioSnapshot.PosicionSnap> posById = index(snap.posiciones(), EscenarioSnapshot.PosicionSnap::id);
        posicionRepo.findAllById(posById.keySet()).forEach(pos -> {
            var s = posById.get(pos.getId());
            pos.setPuntos(s.puntos()); pos.setGanados(s.ganados()); pos.setEmpatados(s.empatados());
            pos.setPerdidos(s.perdidos()); pos.setGolesFavor(s.golesFavor()); pos.setGolesContra(s.golesContra());
            posicionRepo.save(pos);
        });

        Map<Long, EscenarioSnapshot.EquipoSnap> eqById = index(snap.equipos(), EscenarioSnapshot.EquipoSnap::id);
        equipoRepo.findAllById(eqById.keySet()).forEach(eq -> {
            eq.setElo(eqById.get(eq.getId()).elo());
            equipoRepo.save(eq);
        });

        Map<Long, EscenarioSnapshot.EstSnap> ejById = index(snap.estJugador(), EscenarioSnapshot.EstSnap::id);
        ejRepo.findAllById(ejById.keySet()).forEach(st -> { st.setValor(ejById.get(st.getId()).valor()); ejRepo.save(st); });

        Map<Long, EscenarioSnapshot.EstSnap> eeById = index(snap.estEquipo(), EscenarioSnapshot.EstSnap::id);
        eeRepo.findAllById(eeById.keySet()).forEach(st -> { st.setValor(eeById.get(st.getId()).valor()); eeRepo.save(st); });

        Map<Long, EscenarioSnapshot.PrediccionSnap> prById = index(snap.predicciones(), EscenarioSnapshot.PrediccionSnap::id);
        prediccionRepo.findAllById(prById.keySet()).forEach(pr -> {
            pr.setResultadoReal(prById.get(pr.getId()).resultadoReal());
            prediccionRepo.save(pr);
        });

        Map<Long, EscenarioSnapshot.PartidoSnap> paById = index(snap.partidos(), EscenarioSnapshot.PartidoSnap::id);
        partidoRepo.findAllById(paById.keySet()).forEach(p -> {
            var s = paById.get(p.getId());
            p.setEquipoLocal(s.localId() != null ? equipoRepo.getReferenceById(s.localId()) : null);
            p.setEquipoVisitante(s.visitId() != null ? equipoRepo.getReferenceById(s.visitId()) : null);
            p.setGolesLocal(s.gl());
            p.setGolesVisitante(s.gv());
            p.setEstado(s.estado() != null ? EstadoPartido.valueOf(s.estado()) : null);
            p.setRonda(s.ronda() != null ? Ronda.valueOf(s.ronda()) : null);
            partidoRepo.save(p);
        });

        // 3) Reponer el modelo activo (no se borran archivos de modelos creados luego).
        Set<Long> activos = snap.modelos().stream().filter(EscenarioSnapshot.ModeloSnap::activo)
                .map(EscenarioSnapshot.ModeloSnap::id).collect(Collectors.toSet());
        for (ModeloPredictivo m : modeloRepo.findAll()) {
            boolean debeActivo = activos.contains(m.getId());
            if (m.isActivo() != debeActivo) { m.setActivo(debeActivo); modeloRepo.save(m); }
        }
    }

    private <T> Map<Long, T> index(List<T> list, java.util.function.ToLongFunction<T> id) {
        Map<Long, T> m = new LinkedHashMap<>();
        for (T t : list) m.put(id.applyAsLong(t), t);
        return m;
    }

    private Map<String, Object> resumen(Escenario e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("nombre", e.getNombre());
        m.put("descripcion", e.getDescripcion() != null ? e.getDescripcion() : "");
        m.put("creado_en", e.getCreadoEn() != null ? e.getCreadoEn().toString() : null);
        return m;
    }
}
