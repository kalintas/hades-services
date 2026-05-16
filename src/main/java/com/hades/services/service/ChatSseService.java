package com.hades.services.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Server-Sent Event (SSE) emitters keyed by chat session ID.
 *
 * <p>One emitter per session is held in memory. When the assistant message is
 * ready, {@link #pushAssistantMessage} serialises it and sends it as an SSE
 * event named {@code "assistant"}, then completes the emitter.</p>
 */
@Service
public class ChatSseService {

    private static final Logger log = LoggerFactory.getLogger(ChatSseService.class);
    private static final long   SSE_TIMEOUT_MS = 5 * 60 * 1_000L; // 5 minutes

    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ChatSseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates and registers a new {@link SseEmitter} for the given session.
     * Any previous emitter for this session is replaced.
     */
    public SseEmitter register(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(()     -> emitters.remove(sessionId));
        emitter.onError(e        -> emitters.remove(sessionId));

        emitters.put(sessionId, emitter);
        return emitter;
    }

    /**
     * Pushes the assistant message to the registered SSE emitter for the session.
     * If no emitter is registered (client disconnected) the call is a no-op.
     */
    public void pushAssistantMessage(UUID sessionId, ChatMessage message) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter == null) {
            log.warn("[SSE] No active emitter for session {}. Assistant message won't be pushed.", sessionId);
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id",        message.getId().toString());
            payload.put("role",      message.getRole());
            payload.put("content",   message.getContent());
            payload.put("timestamp", message.getTimestamp().toString());

            String json = objectMapper.writeValueAsString(payload);

            emitter.send(SseEmitter.event()
                    .name("assistant")
                    .data(json));
            emitter.complete();

        } catch (Exception e) {
            log.error("[SSE] Failed to push assistant message for session {}: {}", sessionId, e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }

    /**
     * Pushes an error event to the client and closes the emitter.
     */
    public void pushError(UUID sessionId, String errorMessage) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter == null) return;

        try {
            Map<String, String> payload = Map.of("error", errorMessage);
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(payload)));
            emitter.complete();
        } catch (Exception e) {
            log.error("[SSE] Failed to push error for session {}: {}", sessionId, e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }
}
