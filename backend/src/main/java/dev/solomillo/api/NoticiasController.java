package dev.solomillo.api;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Partido;
import dev.solomillo.ml.Prediccion;
import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.NoticiaService;
import dev.solomillo.noticias.PrediccionBackfill;
import dev.solomillo.repository.PrediccionRepository;
import dev.solomillo.repository.TorneoRepository;
import dev.solomillo.users.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class NoticiasController {

    private static final String[] RESULTADO_LABELS = {"Victoria local", "Empate", "Victoria visitante"};

    private final NoticiaService noticias;
    private final PrediccionBackfill backfill;
    private final PrediccionRepository prediccionRepo;
    private final TorneoRepository torneoRepo;
    private final AuditService audit;

    public NoticiasController(NoticiaService noticias, PrediccionBackfill backfill,
                              PrediccionRepository prediccionRepo, TorneoRepository torneoRepo,
                              AuditService audit) {
        this.noticias = noticias;
        this.backfill = backfill;
        this.prediccionRepo = prediccionRepo;
        this.torneoRepo = torneoRepo;
        this.audit = audit;
    }

    @GetMapping("/noticias")
    public List<Map<String, Object>> listar(@RequestParam(required = false) String categoria,
                                            @RequestParam(required = false) String origen) {
        CategoriaNoticia cat = parseCategoria(categoria);
        return noticias.listar(cat, origen);
    }

    @GetMapping("/noticias/categorias")
    public List<Map<String, Object>> categorias() {
        return noticias.categorias();
    }

    @GetMapping("/noticias/{id}")
    public Map<String, Object> detalle(@PathVariable Long id) {
        Map<String, Object> dto = noticias.detalle(id);
        if (dto == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Noticia inexistente");
        return dto;
    }

    @PostMapping("/noticias/regenerar")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> regenerar(@AuthenticationPrincipal String email) {
        int preds = backfill.backfillMundial();
        int generadas = noticias.regenerarTodo();
        audit.registrar(email, "noticias:regenerar", "ok", generadas + " noticias");
        return Map.of("predicciones_backfilled", preds, "noticias", generadas);
    }

    /** "Lo que predijo la IA": predicciones con resultado real, indicando si el modelo acertó. */
    @GetMapping("/predicciones/aciertos")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aciertos() {
        Long mundialId = torneoRepo.findByNombreAndTemporada(NoticiaService.MUNDIAL, NoticiaService.TEMPORADA)
                .map(t -> t.getId()).orElse(null);

        return prediccionRepo.findByResultadoRealIsNotNull().stream()
                .map(Prediccion::getPartido)
                .filter(p -> p != null && p.getEquipoLocal() != null && p.getEquipoVisitante() != null)
                .filter(p -> mundialId == null
                        || (p.getTorneo() != null && mundialId.equals(p.getTorneo().getId())))
                .distinct()
                .sorted(Comparator.comparing((Partido p) ->
                        p.getFechaHora() != null ? p.getFechaHora()
                                : java.time.LocalDateTime.MIN).reversed())
                .map(this::aciertoDto)
                .toList();
    }

    private Map<String, Object> aciertoDto(Partido p) {
        Prediccion pred = prediccionRepo.findFirstByPartidoIdOrderByPredichoEnDesc(p.getId()).orElseThrow();
        double[] probs = {pred.getProbLocal(), pred.getProbEmpate(), pred.getProbVisitante()};
        int real = pred.getResultadoReal() != null ? pred.getResultadoReal()
                : argmaxResultado(p);
        int predicho = argmax(probs);

        Map<String, Object> prob = new LinkedHashMap<>();
        prob.put("local", round(probs[0]));
        prob.put("empate", round(probs[1]));
        prob.put("visitante", round(probs[2]));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("partido_id", p.getId());
        m.put("local", equipoMini(p.getEquipoLocal()));
        m.put("visitante", equipoMini(p.getEquipoVisitante()));
        m.put("marcador", p.getGolesLocal() + "-" + p.getGolesVisitante());
        m.put("fecha", p.getFechaHora() != null ? p.getFechaHora().toString() : null);
        m.put("probabilidades", prob);
        m.put("resultado_real", real);
        m.put("resultado_label", RESULTADO_LABELS[real]);
        m.put("acerto", predicho == real);
        return m;
    }

    private Map<String, Object> equipoMini(Equipo e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("nombre", e.getNombre());
        m.put("escudo", e.getEscudo() != null ? e.getEscudo() : "");
        return m;
    }

    private static int argmaxResultado(Partido p) {
        int gl = p.getGolesLocal() == null ? 0 : p.getGolesLocal();
        int gv = p.getGolesVisitante() == null ? 0 : p.getGolesVisitante();
        return gl > gv ? 0 : gl == gv ? 1 : 2;
    }

    private static int argmax(double[] v) {
        int idx = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[idx]) idx = i;
        return idx;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private CategoriaNoticia parseCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) return null;
        try {
            return CategoriaNoticia.valueOf(categoria.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría inválida: " + categoria);
        }
    }
}
