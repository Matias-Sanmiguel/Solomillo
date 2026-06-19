package dev.solomillo.api;

import dev.solomillo.domain.EstadoPartido;
import dev.solomillo.domain.Partido;
import dev.solomillo.prode.Pronostico;
import dev.solomillo.prode.RankingProdeService;
import dev.solomillo.prode.Signo;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.PronosticoRepository;
import dev.solomillo.repository.UsuarioRepository;
import dev.solomillo.users.AuditService;
import dev.solomillo.users.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/prode")
public class ProdeController {

    private final PronosticoRepository pronosticoRepo;
    private final PartidoRepository partidoRepo;
    private final UsuarioRepository usuarioRepo;
    private final RankingProdeService ranking;
    private final AuditService audit;

    public ProdeController(PronosticoRepository pronosticoRepo, PartidoRepository partidoRepo,
                           UsuarioRepository usuarioRepo, RankingProdeService ranking,
                           AuditService audit) {
        this.pronosticoRepo = pronosticoRepo;
        this.partidoRepo = partidoRepo;
        this.usuarioRepo = usuarioRepo;
        this.ranking = ranking;
        this.audit = audit;
    }

    record PronosticoIn(@NotNull Signo signo, Integer golesLocal, Integer golesVisitante) {}

    /** Partidos abiertos para pronosticar; incluye el pronóstico propio si hay sesión. */
    @GetMapping("/partidos")
    public List<Map<String, Object>> abiertos(@AuthenticationPrincipal String email) {
        Long usuarioId = email != null
                ? usuarioRepo.findByEmail(email).map(Usuario::getId).orElse(null)
                : null;

        return partidoRepo.findByEstadoOrderByFechaHoraAsc(EstadoPartido.PROGRAMADO).stream()
                // Excluye partidos de llave sin rival definido (TBD): no se pueden pronosticar.
                .filter(p -> p.getEquipoLocal() != null && p.getEquipoVisitante() != null)
                .map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partido_id", p.getId());
            m.put("torneo_id", p.getTorneo().getId());
            m.put("local_id", p.getEquipoLocal().getId());
            m.put("visitante_id", p.getEquipoVisitante().getId());
            m.put("fecha_hora", p.getFechaHora() != null ? p.getFechaHora().toString() : null);
            m.put("mi_pronostico", usuarioId == null ? null
                    : pronosticoRepo.findByUsuarioIdAndPartidoId(usuarioId, p.getId())
                        .map(ProdeController::vista).orElse(null));
            return m;
        }).toList();
    }

    /** Crea o actualiza el pronóstico del usuario para un partido. */
    @PutMapping("/pronosticos/{partidoId}")
    public Map<String, Object> guardar(@PathVariable Long partidoId,
                                       @Valid @RequestBody PronosticoIn in,
                                       @AuthenticationPrincipal String email) {
        Usuario usuario = usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Partido partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partido inexistente"));

        if (partido.getEstado() != EstadoPartido.PROGRAMADO
                || (partido.getFechaHora() != null && partido.getFechaHora().isBefore(LocalDateTime.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El partido ya no admite pronósticos");
        }
        if (in.golesLocal() != null && in.golesVisitante() != null
                && Signo.desde(in.golesLocal(), in.golesVisitante()) != in.signo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El resultado exacto no coincide con el signo elegido");
        }

        Pronostico pron = pronosticoRepo.findByUsuarioIdAndPartidoId(usuario.getId(), partidoId)
                .orElseGet(() -> {
                    var nuevo = new Pronostico();
                    nuevo.setUsuario(usuario);
                    nuevo.setPartido(partido);
                    return nuevo;
                });
        pron.setSigno(in.signo());
        pron.setGolesLocal(in.golesLocal());
        pron.setGolesVisitante(in.golesVisitante());
        pronosticoRepo.save(pron);

        audit.registrar(email, "prode:pronostico", "ok", "partido " + partidoId + " " + in.signo());
        return vista(pron);
    }

    @GetMapping("/mis-pronosticos")
    public List<Map<String, Object>> mios(@AuthenticationPrincipal String email) {
        Usuario usuario = usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return pronosticoRepo.findByUsuarioIdOrderByCreadoEnDesc(usuario.getId()).stream()
                .map(ProdeController::vista).toList();
    }

    @GetMapping("/ranking")
    public List<Map<String, Object>> ranking(@RequestParam(required = false) Long torneoId,
                                             @RequestParam(defaultValue = "50") int limite) {
        return ranking.top(torneoId != null ? torneoId : RankingProdeService.GLOBAL, limite);
    }

    @GetMapping("/ranking/me")
    public Map<String, Object> miPosicion(@RequestParam(required = false) Long torneoId,
                                          @AuthenticationPrincipal String email) {
        Usuario usuario = usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ranking.posicionDe(usuario.getId(), torneoId != null ? torneoId : RankingProdeService.GLOBAL);
    }

    private static Map<String, Object> vista(Pronostico p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("partido_id", p.getPartido().getId());
        m.put("signo", p.getSigno() != null ? p.getSigno().name() : null);
        m.put("goles_local", p.getGolesLocal());
        m.put("goles_visitante", p.getGolesVisitante());
        m.put("puntos", p.getPuntos());
        return m;
    }
}
