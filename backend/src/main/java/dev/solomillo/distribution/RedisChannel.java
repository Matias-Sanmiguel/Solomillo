package dev.solomillo.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisChannel implements CanalDistribucion {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisChannel(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void enviar(String topico, Object mensaje) {
        try {
            redis.convertAndSend(topico, mapper.writeValueAsString(mensaje));
        } catch (Exception ignored) {
        }
    }
}
