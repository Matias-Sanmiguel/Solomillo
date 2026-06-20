package dev.solomillo.repository;

import dev.solomillo.noticias.CategoriaNoticia;
import dev.solomillo.noticias.Noticia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoticiaRepository extends JpaRepository<Noticia, Long> {
    List<Noticia> findAllByOrderByRelevanciaDescFechaDesc();
    List<Noticia> findByCategoriaOrderByRelevanciaDescFechaDesc(CategoriaNoticia categoria);
    List<Noticia> findByOrigen(String origen);
    Optional<Noticia> findByClaveNatural(String claveNatural);
}
