package com.hades.services.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.hades.services.model.ChatMessage;
import com.hades.services.model.ChatSession;
import com.hades.services.model.User;
import com.hades.services.service.ChatService;
import com.hades.services.service.ChatSseService;
import com.hades.services.service.UserService;
import com.hades.services.security.annotation.Access;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService    chatService;
    private final ChatSseService chatSseService;
    private final UserService    userService;

    private static final String COOKIE_NAME = "hades_session";

    // ========== SESSION ENDPOINTS ==========

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getSessions(HttpServletRequest request) {
        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<ChatSession> sessions = chatService.getSessions(currentUser.get().getId());

        List<Map<String, Object>> result = sessions.stream().map(session -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id",        session.getId().toString());
            item.put("title",     session.getTitle());
            item.put("createdAt", session.getCreatedAt().toString());
            return item;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String title = payload.getOrDefault("title", "Yeni Sohbet");
        ChatSession session = chatService.createSession(currentUser.get().getId(), title);

        Map<String, Object> result = new HashMap<>();
        result.put("id",        session.getId().toString());
        result.put("title",     session.getTitle());
        result.put("createdAt", session.getCreatedAt().toString());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<ChatSession> session = chatService.getSession(sessionId);
        if (session.isEmpty() || !session.get().getUserId().equals(currentUser.get().getId())) {
            return ResponseEntity.status(403).build();
        }

        chatService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }

    // ========== SSE STREAM ENDPOINT ==========

    /**
     * Client subscribes to this endpoint to receive the assistant response
     * after sending an image message. The emitter is completed once the
     * YOLO→VLM pipeline finishes and the assistant message is pushed.
     *
     * <p>Event name: {@code "assistant"} — payload: JSON with id, role, content, timestamp.</p>
     * <p>Event name: {@code "error"}     — payload: JSON with error string.</p>
     */
    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSession(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {

        // Auth check — same guard as other session endpoints
        Optional<User> currentUser = getCurrentUser(request);
        if (currentUser.isEmpty()) {
            SseEmitter rejected = new SseEmitter(0L);
            rejected.completeWithError(new IllegalStateException("Unauthorized"));
            return rejected;
        }

        Optional<ChatSession> session = chatService.getSession(sessionId);
        if (session.isEmpty() || !session.get().getUserId().equals(currentUser.get().getId())) {
            SseEmitter rejected = new SseEmitter(0L);
            rejected.completeWithError(new IllegalStateException("Forbidden"));
            return rejected;
        }

        return chatSseService.register(sessionId);
    }

    // ========== MESSAGE ENDPOINTS ==========

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getSessionMessages(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Optional<ChatSession> session = chatService.getSession(sessionId);
        if (session.isEmpty() || !session.get().getUserId().equals(currentUser.get().getId())) {
            return ResponseEntity.status(403).build();
        }

        List<ChatMessage> messages = chatService.getSessionMessages(sessionId);

        List<Map<String, Object>> result = messages.stream().map(msg -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id",        msg.getId().toString());
            item.put("role",      msg.getRole());
            item.put("content",   msg.getContent());
            item.put("imageUrl",  msg.getImageUrl());
            item.put("timestamp", msg.getTimestamp().toString());
            return item;
        }).toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Send a message in a session.
     *
     * <p><b>Text-only message (no image):</b> response is returned synchronously
     * in the JSON body: {@code {"response": "..."}}</p>
     *
     * <p><b>Image message:</b> the user message is saved immediately and the
     * YOLO→VLM pipeline starts asynchronously. The response is:
     * {@code {"status": "processing", "userMessageId": "<uuid>"}}.
     * The assistant message will arrive via the SSE stream
     * ({@code GET /chat/sessions/{id}/stream}).</p>
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Map<String, String>> sendMessage(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        String message = payload.get("message");
        String image   = payload.get("image");

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<ChatSession> session = chatService.getSession(sessionId);
        if (session.isEmpty() || !session.get().getUserId().equals(currentUser.get().getId())) {
            return ResponseEntity.status(403).build();
        }

        UUID userId = currentUser.get().getId();

        // Save user message first so it gets an ID and is visible in history
        ChatMessage userMessage = chatService.saveMessage(sessionId, userId, "user", message, image);

        // Load history excluding the just-saved user message
        List<ChatMessage> history = chatService.getSessionMessages(sessionId).stream()
                .filter(m -> !m.getId().equals(userMessage.getId()))
                .toList();

        // Update session title on first message
        Map<String, String> response = new HashMap<>();
        if (history.isEmpty() && message != null && !message.isBlank()) {
            String title = message.length() > 30 ? message.substring(0, 30) + "..." : message;
            chatService.updateSessionTitle(sessionId, title);
            response.put("title", title);
        }

        // Fire YOLO (if image) + VLM — result arrives via SSE regardless of whether image is present
        chatService.processVlmAsync(sessionId, userId, userMessage.getId(), message, image, history);

        response.put("status",        "processing");
        response.put("userMessageId", userMessage.getId().toString());

        return ResponseEntity.ok(response);
    }

    // ========== LEGACY ENDPOINT (anonymous users — text only) ==========

    @PostMapping
    @Access.Public
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        String message = payload.get("message");
        // Anonymous users get the rule-based text response only
        String responseText = chatService.generateTextResponse(message);

        Map<String, String> response = new HashMap<>();
        response.put("response", responseText);

        return ResponseEntity.ok(response);
    }

    // ========== HELPER ==========

    private Optional<User> getCurrentUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String firebaseUid = jwt.getClaimAsString("sub");
                return userService.findByFirebaseUid(firebaseUid);
            }
        }

        if (request.getCookies() != null) {
            Optional<String> token = Arrays.stream(request.getCookies())
                    .filter(c -> COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst();

            if (token.isPresent()) {
                try {
                    FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token.get());
                    String firebaseUid = firebaseToken.getUid();
                    return userService.findByFirebaseUid(firebaseUid);
                } catch (Exception e) {
                    // Invalid token
                }
            }
        }

        return Optional.empty();
    }
}
