package dev.solomillo.api;

import dev.solomillo.sim.EscenarioService;
import dev.solomillo.sim.SimulacionService;
import dev.solomillo.sim.TorneoSimulador;
import dev.solomillo.noticias.NoticiaService;
import dev.solomillo.users.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Endpoints del motor de simulación del Mundial (partido individual, fecha y torneo completo). */
@RestController
@RequestMapping("/sim")
public class SimulacionController {

    private final SimulacionService simulacion;
    private final TorneoSimulador torneo;
    private final EscenarioService escenarios;
    private final AuditService audit;
    private final NoticiaService noticias;

    public SimulacionController(SimulacionService simulacion, TorneoSimulador torneo,
                                EscenarioService escenarios, AuditService audit, NoticiaService noticias) {
        this.simulacion = simulacion;
        this.torneo = torneo;
        this.escenarios = escenarios;
        this.audit = audit;
        this.noticias = noticias;
    }

    @PostMapping("/partidos/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> simularPartido(@PathVariable Long id,
                                              @AuthenticationPrincipal String email) {
        try {
            Map<String, Object> r = simulacion.simularPartido(id, null);
            noticias.generarParaPartido(id);
            audit.registrar(email, "sim:partido", "ok",
                    id + " " + r.get("goles_local") + "-" + r.get("goles_visitante"));
            return r;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            audit.registrar(email, "sim:partido", "error", String.valueOf(e.getMessage()));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/fecha")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> simularFecha(@RequestParam(defaultValue = "false") boolean reentrenar,
                                            @AuthenticationPrincipal String email) {
        try {
            Map<String, Object> r = torneo.simularFecha(reentrenar);
            audit.registrar(email, "sim:fecha", "ok", String.valueOf(r.get("jornada")));
            return r;
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            audit.registrar(email, "sim:fecha", "error", String.valueOf(e.getMessage()));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/mundial")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> simularMundial(@RequestParam(defaultValue = "false") boolean reentrenar,
                                              @AuthenticationPrincipal String email) {
        try {
            Map<String, Object> r = torneo.simularMundial(reentrenar);
            audit.registrar(email, "sim:mundial", "ok", "campeon=" + r.get("campeon"));
            return r;
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            audit.registrar(email, "sim:mundial", "error", String.valueOf(e.getMessage()));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // --- Escenarios (snapshots para comparar simulaciones) ---

    @GetMapping("/escenarios")
    public java.util.List<Map<String, Object>> listarEscenarios() {
        return escenarios.listar();
    }

    @PostMapping("/escenarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> crearEscenario(@RequestBody(required = false) Map<String, String> body,
                                              @AuthenticationPrincipal String email) {
        String nombre = body != null ? body.get("nombre") : null;
        String desc = body != null ? body.get("descripcion") : null;
        Map<String, Object> r = escenarios.crear(nombre, desc);
        audit.registrar(email, "sim:escenario:crear", "ok", String.valueOf(r.get("id")));
        return r;
    }

    @PostMapping("/escenarios/{id}/restaurar")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public Map<String, Object> restaurarEscenario(@PathVariable Long id,
                                                  @AuthenticationPrincipal String email) {
        try {
            Map<String, Object> r = escenarios.restaurar(id);
            audit.registrar(email, "sim:escenario:restaurar", "ok", String.valueOf(id));
            return r;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping("/escenarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('admin_sistema','cientifico_datos')")
    public void eliminarEscenario(@PathVariable Long id, @AuthenticationPrincipal String email) {
        try {
            escenarios.eliminar(id);
            audit.registrar(email, "sim:escenario:eliminar", "ok", String.valueOf(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
