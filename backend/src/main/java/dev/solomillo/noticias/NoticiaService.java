package dev.solomillo.noticias;

import dev.solomillo.domain.Equipo;
import dev.solomillo.domain.Jugador;
import dev.solomillo.domain.Partido;
import dev.solomillo.domain.Torneo;
import dev.solomillo.repository.EquipoRepository;
import dev.solomillo.repository.JugadorRepository;
import dev.solomillo.repository.NoticiaRepository;
import dev.solomillo.repository.PartidoRepository;
import dev.solomillo.repository.TorneoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquesta la generación del feed de noticias a partir de los datos del sistema.
 * Sigue el mismo patrón que {@code MotorProcesamiento}: inyecta la lista de
 * estrategias ({@link DetectorNoticia}) y las recorre. No reimplementa ninguna
 * estadística/predicción: los detectores leen los repositorios existentes.
 */
@Service
public class NoticiaService {

    public static final String MUNDIAL = "Copa Mundial FIFA 2026";
    public static final String TEMPORADA = "2026";

    private final List<DetectorNoticia> detectores;
    private final NoticiaRepository noticiaRepo;
    private final PartidoRepository partidoRepo;
    private final EquipoRepository equipoRepo;
    private final JugadorRepository jugadorRepo;
    private final TorneoRepository torneoRepo;

    public NoticiaService(List<DetectorNoticia> detectores, NoticiaRepository noticiaRepo,
                          PartidoRepository partidoRepo, EquipoRepository equipoRepo,
                          JugadorRepository jugadorRepo, TorneoRepository torneoRepo) {
        this.detectores = detectores;
        this.noticiaRepo = noticiaRepo;
        this.partidoRepo = partidoRepo;
        this.equipoRepo = equipoRepo;
        this.jugadorRepo = jugadorRepo;
        this.torneoRepo = torneoRepo;
    }

    private Long mundialId() {
        return torneoRepo.findByNombreAndTemporada(MUNDIAL, TEMPORADA).map(Torneo::getId).orElse(null);
    }

    /** Genera (upsert) las noticias de un partido recién cerrado. */
    @Transactional
    public void generarParaPartido(Long partidoId) {
        Partido p = partidoRepo.findById(partidoId).orElse(null);
        if (p == null || p.getTorneo() == null) return;
        String origen = p.isSimulado() ? "SIMULADO" : "REAL";
        var ctx = new ContextoNoticia(p.getTorneo().getId(), p, origen);
        for (DetectorNoticia d : detectores) {
            for (Noticia n : d.detectar(ctx)) upsert(n);
        }
    }

    /**
     * Regenera todo el feed desde cero (idempotente): borra las noticias y vuelve
     * a correr todos los detectores en modo batch sobre el Mundial. Usado al
     * arranque y tras avanzar/reset de la simulación.
     */
    @Transactional
    public int regenerarTodo() {
        Long torneoId = mundialId();
        if (torneoId == null) return 0;
        noticiaRepo.deleteAll();
        noticiaRepo.flush();

        boolean hayProyeccion = partidoRepo.findAll().stream().anyMatch(Partido::isSimulado);
        var ctx = new ContextoNoticia(torneoId, null, hayProyeccion ? "SIMULADO" : "REAL");

        int total = 0;
        for (DetectorNoticia d : detectores) {
            for (Noticia n : d.detectar(ctx)) {
                ajustarOrigen(n);
                noticiaRepo.save(n);
                total++;
            }
        }
        return total;
    }

    private void upsert(Noticia candidata) {
        ajustarOrigen(candidata);
        Noticia existente = candidata.getClaveNatural() != null
                ? noticiaRepo.findByClaveNatural(candidata.getClaveNatural()).orElse(null)
                : null;
        if (existente != null) candidata.setId(existente.getId());
        noticiaRepo.save(candidata);
    }

    /** El origen real de una noticia ligada a un partido lo define el flag simulado del partido. */
    private void ajustarOrigen(Noticia n) {
        if (n.getPartidoId() == null) return;
        partidoRepo.findById(n.getPartidoId())
                .ifPresent(p -> n.setOrigen(p.isSimulado() ? "SIMULADO" : "REAL"));
    }

    // ---- Lectura para la API (DTOs enriquecidos con escudos/nombres) ----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(CategoriaNoticia categoria, String origen) {
        List<Noticia> base = categoria != null
                ? noticiaRepo.findByCategoriaOrderByRelevanciaDescFechaDesc(categoria)
                : noticiaRepo.findAllByOrderByRelevanciaDescFechaDesc();
        return base.stream()
                .filter(n -> origen == null || origen.equalsIgnoreCase(n.getOrigen()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Long id) {
        return noticiaRepo.findById(id).map(this::toDto).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> categorias() {
        var todas = noticiaRepo.findAll();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (CategoriaNoticia c : CategoriaNoticia.values()) {
            long cantidad = todas.stream().filter(n -> n.getCategoria() == c).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", c.name());
            m.put("label", c.getEtiqueta());
            m.put("cantidad", cantidad);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> toDto(Noticia n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("titulo", n.getTitulo());
        m.put("subtitulo", n.getSubtitulo());
        m.put("categoria", n.getCategoria().name());
        m.put("categoria_label", n.getCategoria().getEtiqueta());
        m.put("resumen", n.getResumen());
        m.put("fecha", n.getFecha() != null ? n.getFecha().toString() : null);
        m.put("relevancia", n.getRelevancia());
        m.put("origen", n.getOrigen());
        m.put("tags", n.getTags());
        m.put("partido_id", n.getPartidoId());
        m.put("imagen", imagen(n));
        return m;
    }

    private Map<String, Object> imagen(Noticia n) {
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("tipo", n.getImagenTipo());
        img.put("marcador", n.getMarcador());
        img.put("local", equipoMini(n.getEquipoLocalId()));
        img.put("visitante", equipoMini(n.getEquipoVisitanteId()));
        img.put("jugador", jugadorMini(n.getJugadorId()));
        return img;
    }

    private Map<String, Object> equipoMini(Long equipoId) {
        if (equipoId == null) return null;
        Equipo e = equipoRepo.findById(equipoId).orElse(null);
        if (e == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("nombre", e.getNombre());
        m.put("escudo", e.getEscudo() != null ? e.getEscudo() : "");
        return m;
    }

    private Map<String, Object> jugadorMini(Long jugadorId) {
        if (jugadorId == null) return null;
        Jugador j = jugadorRepo.findById(jugadorId).orElse(null);
        if (j == null) return null;
        Equipo e = j.getEquipo();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("nombre", j.getNombre());
        m.put("club", j.getClub() != null ? j.getClub() : "");
        m.put("escudo", e != null && e.getEscudo() != null ? e.getEscudo() : "");
        return m;
    }
}
