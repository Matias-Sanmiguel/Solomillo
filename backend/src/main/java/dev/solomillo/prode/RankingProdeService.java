package dev.solomillo.prode;

import dev.solomillo.repository.RankingProdeRepository;
import dev.solomillo.users.Usuario;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankingProdeService {

    /** Ámbito global del ranking (no asociado a un torneo concreto). */
    public static final long GLOBAL = 0L;

    private final RankingProdeRepository repo;
    private final StringRedisTemplate redis;

    public RankingProdeService(RankingProdeRepository repo, StringRedisTemplate redis) {
        this.repo = repo;
        this.redis = redis;
    }

    /**
     * Acumula los puntos de un pronóstico resuelto en el ranking global y, si
     * corresponde, en el del torneo del partido. Postgres es la verdad; Redis
     * (ZSET por ámbito) actúa como índice rápido para la posición individual.
     */
    @Transactional
    public void registrar(Usuario usuario, Long torneoId, int puntos, boolean acierto) {
        acumular(usuario, GLOBAL, puntos, acierto);
        if (torneoId != null && torneoId != GLOBAL) {
            acumular(usuario, torneoId, puntos, acierto);
        }
    }

    private void acumular(Usuario usuario, long torneoId, int puntos, boolean acierto) {
        RankingProde rp = repo.findByUsuarioIdAndTorneoId(usuario.getId(), torneoId).orElseGet(() -> {
            var nuevo = new RankingProde();
            nuevo.setUsuarioId(usuario.getId());
            nuevo.setTorneoId(torneoId);
            nuevo.setNombre(usuario.getNombre());
            return nuevo;
        });
        rp.setPuntos(rp.getPuntos() + puntos);
        rp.setPronosticos(rp.getPronosticos() + 1);
        if (acierto) rp.setAciertos(rp.getAciertos() + 1);
        repo.save(rp);

        redis.opsForZSet().incrementScore(zkey(torneoId), String.valueOf(usuario.getId()), puntos);
    }

    /** Tabla de posiciones de un ámbito (Postgres conserva el nombre denormalizado). */
    public List<Map<String, Object>> top(long torneoId, int limite) {
        List<Map<String, Object>> out = new ArrayList<>();
        int pos = 1;
        for (RankingProde rp : repo.findByTorneoIdOrderByPuntosDescAciertosDesc(torneoId)) {
            if (pos > limite) break;
            out.add(fila(pos++, rp));
        }
        return out;
    }

    /** Posición individual: usa el rank O(log n) de Redis, con fallback a Postgres. */
    public Map<String, Object> posicionDe(Long usuarioId, long torneoId) {
        RankingProde rp = repo.findByUsuarioIdAndTorneoId(usuarioId, torneoId).orElse(null);
        if (rp == null) {
            return Map.of("posicion", 0, "puntos", 0, "aciertos", 0, "pronosticos", 0);
        }
        Long rank = redis.opsForZSet().reverseRank(zkey(torneoId), String.valueOf(usuarioId));
        long posicion = rank != null
                ? rank + 1
                : repo.countByTorneoIdAndPuntosGreaterThan(torneoId, rp.getPuntos()) + 1;
        return fila(posicion, rp);
    }

    private String zkey(long torneoId) {
        return torneoId == GLOBAL ? "ranking:prode:global" : "ranking:prode:torneo:" + torneoId;
    }

    private Map<String, Object> fila(long posicion, RankingProde rp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("posicion", posicion);
        m.put("usuario_id", rp.getUsuarioId());
        m.put("nombre", rp.getNombre());
        m.put("puntos", rp.getPuntos());
        m.put("aciertos", rp.getAciertos());
        m.put("pronosticos", rp.getPronosticos());
        return m;
    }
}
