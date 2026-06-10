package dev.solomillo.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Component
@ConditionalOnProperty(name = "app.webhook.url")
public class WebhookChannel implements CanalDistribucion {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannel.class);

    private final String url;
    private final RestTemplate http = new RestTemplate();

    public WebhookChannel(@Value("${app.webhook.url}") String url) {
        this.url = url;
    }

    @Override
    public void enviar(String topico, Object mensaje) {
        if (!"alerta".equals(topico)) return;
        try {
            http.exchange(RequestEntity.post(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mensaje), String.class);
        } catch (Exception ex) {
            log.warn("Fallo al notificar alerta al webhook {}: {}", url, ex.getMessage());
        }
    }
}
