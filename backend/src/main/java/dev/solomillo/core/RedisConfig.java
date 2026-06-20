package dev.solomillo.core;

import dev.solomillo.api.EventosWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    @Bean
    RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory factory, EventosWebSocketHandler handler) {
        var adapter = new MessageListenerAdapter(handler, "onRedisMessage");
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(adapter, new ChannelTopic("evento"));
        container.addMessageListener(adapter, new ChannelTopic("alerta"));
        return container;
    }
}
