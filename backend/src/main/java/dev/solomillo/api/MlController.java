package dev.solomillo.api;

import dev.solomillo.ml.MlAnalytics;
import dev.solomillo.ml.MlPredictor;
import dev.solomillo.ml.MlTrainer;
import dev.solomillo.ml.ModeloPredictivo;
import dev.solomillo.repository.ModeloPredictivoRepository;
import dev.solomillo.users.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ml")
public class MlController {

    private static final String[] ROLES_ML = {"admin_modelos_ia", "cientifico_datos"};

    private final MlTrainer trainer;
    private final MlPredictor predictor;
    private final MlAnalytics analytics;
    private final ModeloPredictivoRepository modeloRepo;
    private final AuditService audit;

    public MlController(MlTrainer t, MlPredictor p, MlAnalytics a,
                        ModeloPredictivoRepository r, AuditService au) {
        this.trainer = t; this.predictor = p; this.analytics = a;
        this.modeloRepo = r; this.audit = au;
    }

    @PostMapping("/modelos/entrenar")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('admin_modelos_ia','cientifico_datos')")
    public Map<String, Object> entrenar(@AuthenticationPrincipal String email) {
        try {
            ModeloPredictivo m = trainer.entrenarResultado();
            audit.registrar(email, "ml:entrenar:resultado", "ok", "v" + m.getVersion());
            return Map.of("version", m.getVersion(), "accuracy", m.getAccuracy(), "activo", m.isActivo());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/modelos/entrenar-rendimiento")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('admin_modelos_ia','cientifico_datos')")
    public Map<String, Object> entrenarRendimiento(@AuthenticationPrincipal String email) {
        try {
            ModeloPredictivo m = trainer.entrenarRendimiento();
            audit.registrar(email, "ml:entrenar:rendimiento", "ok", "v" + m.getVersion());
            return Map.of("version", m.getVersion(), "r2", m.getAccuracy(), "activo", m.isActivo());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/modelos")
    public List<Map<String, Object>> modelos() {
        return modeloRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
                .map(m -> Map.<String, Object>of("version", m.getVersion(), "tipo", m.getTipo(),
                        "accuracy", m.getAccuracy(), "activo", m.isActivo()))
                .toList();
    }

    @GetMapping("/predicciones/{partidoId}")
    public Map<String, Object> prediccion(@PathVariable Long partidoId) {
        try {
            return predictor.predecirResultado(partidoId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/rendimiento/{jugadorId}")
    public Map<String, Object> rendimiento(@PathVariable Long jugadorId) {
        try {
            return predictor.rendimientoJugador(jugadorId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/proyeccion/{torneoId}")
    public List<Map<String, Object>> proyeccion(@PathVariable Long torneoId) {
        try {
            return analytics.proyectar(torneoId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/tendencias/{torneoId}")
    public List<Map<String, Object>> tendencias(@PathVariable Long torneoId) {
        return analytics.tendencias(torneoId);
    }
}
