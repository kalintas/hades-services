package com.hades.services.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hades.services.service.sagemaker.dto.YoloParams;
import com.hades.services.model.ChatMessage;
import com.hades.services.model.ChatSession;
import com.hades.services.model.YoloInference;
import com.hades.services.repository.ChatMessageRepository;
import com.hades.services.repository.ChatSessionRepository;
import com.hades.services.repository.YoloInferenceRepository;
import com.hades.services.service.sagemaker.VlmInferenceService;
import com.hades.services.service.sagemaker.YoloInferenceService;
import com.hades.services.service.sagemaker.dto.SageMakerVlmRequest;
import com.hades.services.service.sagemaker.dto.SageMakerVlmResponse;
import com.hades.services.service.sagemaker.dto.SageMakerYoloDetection;
import com.hades.services.service.sagemaker.dto.SageMakerYoloResponse;
import com.hades.services.service.sagemaker.dto.VlmHistoryEntry;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository    chatMessageRepository;
    private final ChatSessionRepository    chatSessionRepository;
    private final YoloInferenceService     yoloInferenceService;
    private final VlmInferenceService      vlmInferenceService;
    private final AwsFileService           awsFileService;
    private final ObjectMapper             objectMapper;
    private final YoloInferenceRepository  yoloInferenceRepository;
    private final ChatSseService           chatSseService;

    @Value("${hades.content.domain}")
    private String contentDomain;

    // ─────────────────────────────────────────────────────────────────────────
    // Session management
    // ─────────────────────────────────────────────────────────────────────────

    public ChatSession createSession(UUID userId, String title) {
        ChatSession session = new ChatSession(userId, title);
        return chatSessionRepository.save(session);
    }

    public List<ChatSession> getSessions(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<ChatSession> getSession(UUID sessionId) {
        return chatSessionRepository.findById(sessionId);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    public void updateSessionTitle(UUID sessionId, String title) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setTitle(title);
            chatSessionRepository.save(session);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves a user message. If an image URL is attached, kicks off the
     * YOLO → VLM async pipeline in the background.
     *
     * @param sessionId  target session (may be null for anonymous/legacy)
     * @param userId     owning user
     * @param role       "user" or "assistant"
     * @param content    message text
     * @param imageUrl   optional image URL or base-64 data URI
     * @return the saved message
     */
    public ChatMessage saveMessage(UUID sessionId, UUID userId, String role, String content, String imageUrl) {
        ChatMessage message = new ChatMessage(sessionId, userId, role, content, imageUrl);
        // YOLO + VLM are handled together in processVlmAsync, not here.
        return chatMessageRepository.save(message);
    }

    /**
     * Starts the async pipeline for any message (text-only or image).
     *
     * <ul>
     *   <li>If {@code imageUrl} is non-blank: image is downloaded, YOLO runs (fault-tolerant),
     *       and detections are injected as a [SİSTEM VERİSİ] block.</li>
     *   <li>If {@code imageUrl} is blank/null: the pipeline skips the image/YOLO steps
     *       and calls the VLM as a text-only conversation.</li>
     *   <li>{@code history} is the ordered list of previous messages in this session
     *       (excluding the current user message). It is serialised into the prompt.</li>
     * </ul>
     *
     * The assistant reply is saved to the DB and pushed to the SSE stream when ready.
     */
    public void processVlmAsync(
            UUID sessionId,
            UUID userId,
            UUID userMessageId,
            String userMessage,
            String imageUrl,
            List<ChatMessage> history) {

        CompletableFuture.runAsync(() -> {
            try {
                byte[] imageBytes = null;

                // ── 1. Image + YOLO (only when image is present) ────────────
                String systemVeri = null;
                boolean hasImage = imageUrl != null && !imageUrl.isBlank();

                if (hasImage) {
                    imageBytes = resolveImageBytes(imageUrl);

                    try {
                        YoloParams yoloParams = new YoloParams();
                        SageMakerYoloResponse yoloResponse = yoloInferenceService.infer(imageBytes, yoloParams);

                        // Persist the YOLO result
                        YoloInference yoloRecord = new YoloInference();
                        yoloRecord.setMessageId(userMessageId);
                        yoloRecord.setError(yoloResponse.error());
                        if (yoloResponse.error() != null) {
                            System.err.println("[YOLO] Endpoint returned an error: " + yoloResponse.error());
                        } else {
                            yoloRecord.setDetectionsJson(objectMapper.writeValueAsString(yoloResponse.detections()));
                            yoloRecord.setImageWidth(yoloResponse.imageWidth());
                            yoloRecord.setImageHeight(yoloResponse.imageHeight());
                            yoloRecord.setInferenceTimeMs(yoloResponse.inferenceTimeMs());
                            if (yoloResponse.detections() != null && !yoloResponse.detections().isEmpty()) {
                                systemVeri = buildSystemVeri(yoloResponse.detections());
                            }
                        }
                        yoloInferenceRepository.save(yoloRecord);

                    } catch (Exception yoloEx) {
                        System.err.println("[YOLO] Could not reach inference endpoint — skipping sistem verisi injection. Cause: " + yoloEx.getMessage());
                        YoloInference errRecord = new YoloInference();
                        errRecord.setMessageId(userMessageId);
                        errRecord.setError(yoloEx.getMessage());
                        yoloInferenceRepository.save(errRecord);
                    }
                }

                // ── 2. Build history list + optional YOLO block appended to prompt ──
                List<VlmHistoryEntry> historyEntries = buildVlmHistory(history);

                // Append YOLO [SİSTEM VERİSİ] block to the current user message if available
                String prompt = (systemVeri != null && !systemVeri.isBlank())
                        ? systemVeri + "\n\n" + userMessage
                        : userMessage;

                // ── 3. VLM inference ────────────────────────────────────────
                SageMakerVlmRequest vlmRequest = new SageMakerVlmRequest(
                        "", prompt, historyEntries, 2048, false);
                SageMakerVlmResponse vlmResponse = vlmInferenceService.infer(imageBytes, vlmRequest);

                String assistantContent;
                if (vlmResponse.error() != null && !vlmResponse.error().isBlank()) {
                    log.error("[VLM] Endpoint returned an error for session {}: {}", sessionId, vlmResponse.error());
                    assistantContent = "Yanıt oluşturulurken bir hata oluştu. Lütfen tekrar deneyin.";
                } else {
                    assistantContent = vlmResponse.response();
                }

                // ── 4. Save assistant message ────────────────────────────────
                ChatMessage assistantMessage = chatMessageRepository.save(
                        new ChatMessage(sessionId, userId, "assistant", assistantContent, null)
                );

                // ── 5. Push via SSE ──────────────────────────────────────────
                chatSseService.pushAssistantMessage(sessionId, assistantMessage);

            } catch (Exception e) {
                log.error("[VLM] Async pipeline failed for session {}: {}", sessionId, e.getMessage(), e);
                chatSseService.pushError(sessionId, "Mesaj işlenirken bir hata oluştu.");
            }
        });
    }


    public List<ChatMessage> getSessionMessages(UUID sessionId) {
        return chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Text-only rule-based response (synchronous, no image)
    // ─────────────────────────────────────────────────────────────────────────

    public String generateTextResponse(String message) {
        if (message == null) {
            return "Anlaşılmadı, lütfen tekrar edin.";
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("merhaba") || lowerMessage.contains("selam")) {
            return "Merhaba! Size nasıl yardımcı olabilirim? Deprem güvenliği, hasar tespiti veya acil durum prosedürleri hakkında sorularınızı yanıtlayabilirim.";
        } else if (lowerMessage.contains("hasar") || lowerMessage.contains("çatlak")) {
            return "Hasar tespiti yapmak için binanın hasarlı bölgesinin fotoğrafını yükleyebilir misiniz? Fotoğraf üzerinden ön değerlendirme yapabilirim.";
        } else if (lowerMessage.contains("deprem") || lowerMessage.contains("sarsıntı")) {
            return "Deprem anında 'Çök-Kapan-Tutun' pozisyonunu almalısınız. Sarsıntı geçtikten sonra binayı güvenli bir şekilde tahliye edin ve toplanma alanlarına gidin.";
        } else if (lowerMessage.contains("drone") || lowerMessage.contains("görüntü")) {
            return "Drone görüntülerini analiz ederek geniş alanlardaki hasarı haritalandırabilir ve ulaşılması zor bölgelerdeki yıkımı tespit edebilirim.";
        } else if (lowerMessage.contains("acil") || lowerMessage.contains("112") || lowerMessage.contains("afad")
                || lowerMessage.contains("telefon") || lowerMessage.contains("numara")) {
            return "🚨 **Acil Durum Numaraları:**\n- **112**: Acil Çağrı Merkezi (Ambulans, Polis, İtfaiye)\n- **122**: AFAD\n- **177**: Orman Yangını İhbar\nLütfen hattı gereksiz meşgul etmeyiniz.";
        } else if (lowerMessage.contains("toplanma") || lowerMessage.contains("alan")
                || lowerMessage.contains("nerede") || lowerMessage.contains("konum")) {
            return "📍 Size en yakın toplanma alanını e-Devlet üzerinden 'Afet ve Acil Durum Toplanma Alanı Sorgulama' hizmetini kullanarak öğrenebilirsiniz. Güvenliğiniz için lütfen hasarlı binalardan uzak durun.";
        } else if (lowerMessage.contains("yardım") || lowerMessage.contains("ilk yardım")
                || lowerMessage.contains("yaralı") || lowerMessage.contains("kanama")) {
            if (lowerMessage.contains("ilk") || lowerMessage.contains("yaralı")) {
                return "🩹 **Temel İlk Yardım:**\n1. Önce kendi güvenliğinizi sağlayın.\n2. Yaralını hareket ettirmeyin (hayati tehlike yoksa).\n3. Kanama varsa temiz bir bezle baskı uygulayın.\n4. Yaralıyı sıcak tutun ve hemen 112'yi arayın.";
            } else {
                return "ℹ️ **Size şu konularda yardımcı olabilirim:**\n- 'Hasar bildir' yazarak fotoğraf yükleyebilirsiniz.\n- 'Acil numaralar' yazarak iletişim listesini görebilirsiniz.\n- 'Deprem anında ne yapmalıyım?' diye sorabilirsiniz.\n- 'Toplanma alanı' hakkında bilgi alabilirsiniz.";
            }
        } else if (lowerMessage.contains("teşekkür") || lowerMessage.contains("sağol")) {
            return "Rica ederim. Lütfen dikkatli olun ve güvende kalın. 🙏";
        } else {
            return "Bu konuda size şu an yardımcı olamıyorum. 'Yardım' yazarak neler yapabileceğimi görebilirsiniz.";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] resolveImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String contentUrlPrefix = "https://" + contentDomain + "/";
        if (imageUrl.startsWith(contentUrlPrefix)) {
            String filePath = imageUrl.substring(contentUrlPrefix.length());
            return awsFileService.downloadFile(filePath);
        } else if (imageUrl.startsWith("data:image")) {
            String base64 = imageUrl.substring(imageUrl.indexOf(",") + 1);
            return Base64.getDecoder().decode(base64);
        } else {
            throw new IllegalArgumentException("Unsupported image URL format: " + imageUrl);
        }
    }

    /**
     * Converts previous ChatMessage turns into a list of VlmHistoryEntry for
     * the VLM endpoint, preserving the role labels the model understands.
     */
    private List<VlmHistoryEntry> buildVlmHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        List<VlmHistoryEntry> entries = new ArrayList<>(history.size());
        for (ChatMessage msg : history) {
            entries.add(new VlmHistoryEntry(msg.getRole(), msg.getContent()));
        }
        return entries;
    }

    /**
     * Builds the [SİSTEM VERİSİ] injection block from YOLO detections.
     */
    private String buildSystemVeri(List<SageMakerYoloDetection> detections) {
        String detectionLines = detections.stream()
                .map(d -> String.format("- %s (güven: %.2f)", d.className(), d.confidence()))
                .collect(Collectors.joining("\n"));

        return "[SİSTEM VERİSİ]\n" +
               "Tespit edilen nesneler:\n" +
               detectionLines + "\n" +
               "[/SİSTEM VERİSİ]";
    }

}
