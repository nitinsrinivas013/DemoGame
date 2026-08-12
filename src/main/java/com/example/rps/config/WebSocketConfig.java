package com.example.rps.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP over WebSocket.
 *
 * - Endpoint: /ws
 * - Client -> Server prefix: /app
 * - Server -> Client (broadcast) prefix: /topic
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker for broadcasting to /topic/**
        registry.enableSimpleBroker("/topic");
        // Messages sent from clients are routed to @MessageMapping methods prefixed with /app
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint (used by the STOMP JS client). SockJS is not required
        // since we target modern mobile/desktop browsers only.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
