package dev.solomillo.noticias;

/**
 * Categorías editoriales del feed de noticias. El nombre del enum se usa como
 * filtro en la API (?categoria=GOLEADA) y como prefijo de la clave natural de
 * deduplicación de cada {@link Noticia}.
 */
public enum CategoriaNoticia {
    RESULTADO_INESPERADO("Resultado inesperado"),
    BATACAZO("Batacazo"),
    FIGURA("Figura del partido"),
    GOLEADA("Goleada"),
    RANKING_ELO("Ranking Elo"),
    RECORD("Récords");

    private final String etiqueta;

    CategoriaNoticia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
