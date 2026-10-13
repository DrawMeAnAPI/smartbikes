package com.pjmbusnel.smartbikes.web;

import com.pjmbusnel.smartbikes.io.Json;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code /fleet} endpoint: tracks connected browsers and pushes messages to them.
 * Incoming frames of any kind are ignored (the connection is only kept alive).
 */
@Component
public class FleetWebSocketHandler extends AbstractWebSocketHandler {

    private final Set<WebSocketSession> sessions = Collections.synchronizedSet(new LinkedHashSet<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.out.println("Connection closed: " + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        broadcastSessionCount(); // Broadcast update on leave
    }

    /**
     * Sends {@code json} to every connected session, each send on its own virtual thread.
     */
    public void broadcast(String json) {
        synchronized (sessions) {
            for (WebSocketSession session : List.copyOf(sessions)) {
                Thread.ofVirtual().start(() -> send(session, json));
            }
        }
    }

    // Helper function to send the count to all active browsers
    private void broadcastSessionCount() {
        String message = sessionCountJson(sessions.size());

        synchronized (sessions) {
            for (WebSocketSession session : sessions) {
                Thread.ofVirtual().start(() -> send(session, message));
            }
        }
    }

    public static String sessionCountJson(int count) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "SESSION_COUNT");
        message.put("count", count);
        return Json.MAPPER.writeValueAsString(message);
    }

    private static void send(WebSocketSession session, String text) {
        try {
            // WebSocketSession is not thread-safe: one send at a time per session
            synchronized (session) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            // Session might have closed during broadcast; removal is handled by afterConnectionClosed
        }
    }
}