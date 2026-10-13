package com.pjmbusnel.smartbikes.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final FleetWebSocketHandler fleetHandler;

    public WebSocketConfig(FleetWebSocketHandler fleetHandler) {
        this.fleetHandler = fleetHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Any origin: index.html is opened straight from disk (Origin "null")
        registry.addHandler(fleetHandler, "/fleet").setAllowedOriginPatterns("*");
    }
}