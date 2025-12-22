package com.hades.services.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.hades.services.model.ChatMessage;
import com.hades.services.model.ChatSession;
import com.hades.services.model.User;
import com.hades.services.service.ChatService;
import com.hades.services.service.UserService;
import com.hades.services.security.annotation.Access;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private static final String COOKIE_NAME = "hades_session";

    // ========== SESSION ENDPOINTS ==========

    @GetMapping("/sessions")
    @Access.Public
    public ResponseEntity<List<Map<String, Object>>> getSessions(HttpServletRequest request) {
        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<ChatSession> sessions = chatService.getSessions(currentUser.get().getId());

        List<Map<String, Object>> result = sessions.stream().map(session -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", session.getId().toString());
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreatedAt().toString());
            return item;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/sessions")
    @Access.Public
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
        result.put("id", session.getId().toString());
        result.put("title", session.getTitle());
        result.put("createdAt", session.getCreatedAt().toString());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Access.Public
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        // Verify session belongs to user
        Optional<ChatSession> session = chatService.getSession(sessionId);
        if (session.isEmpty() || !session.get().getUserId().equals(currentUser.get().getId())) {
            return ResponseEntity.status(403).build();
        }

        chatService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }

    // ========== MESSAGE ENDPOINTS ==========

    @GetMapping("/sessions/{sessionId}/messages")
    @Access.Public
    public ResponseEntity<List<Map<String, Object>>> getSessionMessages(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Verify session belongs to user
        Optional<ChatSession> session = chatService.getSession(sessionId);
        if (session.isEmpty() || !session.get().getUserId().equals(currentUser.get().getId())) {
            return ResponseEntity.status(403).build();
        }

        List<ChatMessage> messages = chatService.getSessionMessages(sessionId);

        List<Map<String, Object>> result = messages.stream().map(msg -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", msg.getId().toString());
            item.put("role", msg.getRole());
            item.put("content", msg.getContent());
            item.put("timestamp", msg.getTimestamp().toString());
            return item;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @Access.Public
    public ResponseEntity<Map<String, String>> sendMessage(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        String message = payload.get("message");
        String responseText = chatService.generateResponse(message);

        Optional<User> currentUser = getCurrentUser(request);

        if (currentUser.isPresent()) {
            // Verify session belongs to user
            Optional<ChatSession> session = chatService.getSession(sessionId);
            if (session.isPresent() && session.get().getUserId().equals(currentUser.get().getId())) {
                // Save messages
                chatService.saveMessage(sessionId, currentUser.get().getId(), "user", message);
                chatService.saveMessage(sessionId, currentUser.get().getId(), "assistant", responseText);

                // Update session title if it's the first message
                List<ChatMessage> messages = chatService.getSessionMessages(sessionId);
                if (messages.size() <= 2) { // Just added first user + assistant message
                    String title = message.length() > 30 ? message.substring(0, 30) + "..." : message;
                    chatService.updateSessionTitle(sessionId, title);
                }
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("response", responseText);

        return ResponseEntity.ok(response);
    }

    // ========== LEGACY ENDPOINT (for anonymous users) ==========

    @PostMapping
    @Access.Public
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        String message = payload.get("message");
        String responseText = chatService.generateResponse(message);

        Map<String, String> response = new HashMap<>();
        response.put("response", responseText);

        return ResponseEntity.ok(response);
    }

    // ========== HELPER ==========

    private Optional<User> getCurrentUser(HttpServletRequest request) {
        // First try SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String firebaseUid = jwt.getClaimAsString("sub");
                return userService.findByFirebaseUid(firebaseUid);
            }
        }

        // Fallback: manually parse cookie
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
