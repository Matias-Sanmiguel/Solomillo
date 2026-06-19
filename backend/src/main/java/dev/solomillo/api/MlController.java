package dev.solomillo.api;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.ml.MetricsService;
import dev.solomillo.ml.MlPredictor;
import dev.solomillo.ml.MlTrainer;
import dev.solomillo.ml.ModeloPredictivo;
import dev.solomillo.repository.EloHistorialRepository;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.users.AuditService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ml")
public class MlController {

    private final MlTrainer trainer;
    private final MlPredictor predictor;
    private final MetricsService metrics;
    private final ModeloPredictivoRepository modeloRepo;
    private final PartidoRepository partidoRepo;
    private final EquipoRepository equipoRepo;
    private final EloHistorialRepository eloHistRepo;
    private final AuditService audit;

    public MlController(
        MlTrainer t,
        MlPredictor p,
        MetricsService metrics,
        ModeloPredictivoRepository r,
        PartidoRepository partidoRepo,
        EquipoRepository equipoRepo,
        EloHistorialRepository eloHistRepo,
        AuditService au
    ) {
        this.trainer = t;
        this.predictor = p;
        this.metrics = metrics;
        this.modeloRepo = r;
        this.partidoRepo = partidoRepo;
        this.equipoRepo = equipoRepo;
        this.eloHistRepo = eloHistRepo;
        this.audit = au;
    }

    @PostMapping("/modelos/entrenar")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('admin_modelos_ia','cientifico_datos')")
    public Map<String, Object> entrenar(@AuthenticationPrincipal String email) {
        try {
            ModeloPredictivo m = trainer.entrenarResultado();
            audit.registrar(
                email,
                "ml:entrenar:resultado",
                "ok",
                "v" + m.getVersion()
            );
            return Map.of(
                "version",
                m.getVersion(),
                "accuracy",
                m.getAccuracy(),
                "log_loss",
                m.getLogLoss(),
                "brier",
                m.getBrier(),
                "activo",
                m.isActivo()
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                e.getMessage()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
            );
        }
    }

    @PostMapping("/modelos/entrenar-rendimiento")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('admin_modelos_ia','cientifico_datos')")
    public Map<String, Object> entrenarRendimiento(
        @AuthenticationPrincipal String email
    ) {
        try {
            ModeloPredictivo m = trainer.entrenarRendimiento();
            audit.registrar(
                email,
                "ml:entrenar:rendimiento",
                "ok",
                "v" + m.getVersion()
            );
            return Map.of(
                "version",
                m.getVersion(),
                "r2",
                m.getAccuracy(),
                "activo",
                m.isActivo()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
            );
        }
    }

    @GetMapping("/modelos")
    public List<Map<String, Object>> modelos() {
        return modeloRepo
            .findAll()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
            .map(m ->
                Map.<String, Object>of(
                    "version",
                    m.getVersion(),
                    "tipo",
                    m.getTipo(),
                    "accuracy",
                    m.getAccuracy(),
                    "log_loss",
                    m.getLogLoss(),
                    "brier",
                    m.getBrier(),
                    "activo",
                    m.isActivo()
                )
            )
            .toList();
    }

    @GetMapping("/modelos/{version}/metricas")
    public Map<String, Object> metricas(@PathVariable int version) {
        return metrics.metricas(version);
    }

    @GetMapping("/calibracion/{version}")
    public Map<String, Object> calibracion(@PathVariable int version) {
        return metrics.calibracion(version);
    }

    @GetMapping("/predicciones/{partidoId}")
    public Map<String, Object> prediccion(@PathVariable Long partidoId) {
        try {
            return predictor.predecirResultado(partidoId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                e.getMessage()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                e.getMessage()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
            );
        }
    }

    @GetMapping("/predicciones")
    public List<Map<String, Object>> tablero() {
        List<Partido> proximos = new ArrayList<>();
        proximos.addAll(
            partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.EN_VIVO)
        );
        proximos.addAll(
            partidoRepo.findByEstadoOrderByFechaHoraAsc(
                EstadoPartido.PROGRAMADO
            )
        );
        List<Map<String, Object>> out = new ArrayList<>();
        for (Partido p : proximos) {
            try {
                out.add(predictor.tablero(p.getId()));
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
                );
            } catch (Exception ignore) {
            }
        }
        return out;
    }

    @GetMapping("/rendimiento/{jugadorId}")
    public Map<String, Object> rendimiento(@PathVariable Long jugadorId) {
        try {
            return predictor.rendimientoJugador(jugadorId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                e.getMessage()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
            );
        }
    }

    @GetMapping("/elo")
    public List<Map<String, Object>> elo() {
        return equipoRepo
            .findAll()
            .stream()
            .sorted(
                Comparator.comparingDouble((dev.solomillo.domain.Equipo e) ->
                    e.getElo() != null ? e.getElo() : 0
                ).reversed()
            )
            .map(e ->
                Map.<String, Object>of(
                    "equipo_id",
                    e.getId(),
                    "nombre",
                    e.getNombre(),
                    "escudo",
                    e.getEscudo() != null ? e.getEscudo() : "",
                    "elo",
                    Math.round((e.getElo() != null ? e.getElo() : 0) * 10.0) /
                        10.0,
                    "puntos_fifa",
                    e.getPuntosFifa() != null ? e.getPuntosFifa() : 0
                )
            )
            .toList();
    }

    @GetMapping("/elo/{equipoId}/historial")
    public List<Map<String, Object>> eloHistorial(@PathVariable Long equipoId) {
        return eloHistRepo
            .findByEquipoIdOrderByFechaAsc(equipoId)
            .stream()
            .map(h ->
                Map.<String, Object>of(
                    "fecha",
                    h.getFecha().toString(),
                    "elo",
                    Math.round(h.getElo() * 10.0) / 10.0
                )
            )
            .toList();
    }
}
