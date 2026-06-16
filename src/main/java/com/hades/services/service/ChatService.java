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

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final YoloInferenceService yoloInferenceService;
    private final VlmInferenceService vlmInferenceService;
    private final AwsFileService awsFileService;
    private final ObjectMapper objectMapper;
    private final YoloInferenceRepository yoloInferenceRepository;
    private final ChatSseService chatSseService;

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

    public ChatMessage saveMessage(UUID sessionId, UUID userId, String role, String content, String imageUrl) {
        ChatMessage message = new ChatMessage(sessionId, userId, role, content, imageUrl);
        return chatMessageRepository.save(message);
    }

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
                String systemVeri = null;
                boolean hasImage = imageUrl != null && !imageUrl.isBlank();
                SageMakerYoloResponse yoloResponse = null;

                if (hasImage) {
                    imageBytes = resolveImageBytes(imageUrl);
                    try {
                        YoloParams yoloParams = new YoloParams();
                        yoloResponse = yoloInferenceService.infer(imageBytes, yoloParams);

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
                        System.err.println("[YOLO] Skipping system data injection. Cause: " + yoloEx.getMessage());
                        YoloInference errRecord = new YoloInference();
                        errRecord.setMessageId(userMessageId);
                        errRecord.setError(yoloEx.getMessage());
                        yoloInferenceRepository.save(errRecord);
                    }
                }

                List<VlmHistoryEntry> historyEntries = buildVlmHistory(history);
                String prompt = (systemVeri != null && !systemVeri.isBlank())
                        ? systemVeri + "\n\n" + userMessage
                        : userMessage;

                SageMakerVlmRequest vlmRequest = vlmInferenceService.buildRequest(
                        historyEntries, prompt, imageBytes, 2048, true);
                SageMakerVlmResponse vlmResponse = vlmInferenceService.infer(imageBytes, vlmRequest);

                String assistantContent = Optional.ofNullable(vlmResponse.choices().get(0).message().content())
                        .filter(s -> !s.isBlank())
                        .orElseGet(() -> Optional.ofNullable(vlmResponse.choices().get(0).message().reasoningContent())
                                .orElse(""));

                // ── 4. Match VLM boxes to YOLO masks ─────────────────────────
                List<SageMakerYoloDetection> matchedDetections = new ArrayList<>();
                if (yoloResponse != null && yoloResponse.detections() != null) {
                    assistantContent = matchAndEnrichDetections(
                            assistantContent,
                            yoloResponse.detections(),
                            matchedDetections);
                }

                // ── 5. Save assistant message ────────────────────────────────
                ChatMessage assistantMessage = new ChatMessage(sessionId, userId, "assistant", assistantContent, null);
                if (!matchedDetections.isEmpty()) {
                    assistantMessage.setDetectionsJson(objectMapper.writeValueAsString(matchedDetections));
                }
                assistantMessage = chatMessageRepository.save(assistantMessage);

                // ── 6. Push via SSE ──────────────────────────────────────────
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

    public String generateTextResponse(String message) {
        try {
            SageMakerVlmRequest vlmRequest = vlmInferenceService.buildRequest(
                    List.of(), message, null, 2048, true);
            SageMakerVlmResponse vlmResponse = vlmInferenceService.infer(null, vlmRequest);
            SageMakerVlmResponse.Message msg = vlmResponse.choices().get(0).message();
            return Optional.ofNullable(msg.content())
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> Optional.ofNullable(msg.reasoningContent()).orElse(""));
        } catch (Exception e) {
            log.error("[VLM] generateTextResponse failed: {}", e.getMessage(), e);
            throw new RuntimeException("Yanıt oluşturulamadı", e);
        }
    }

    private byte[] resolveImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank())
            return null;
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

    private List<VlmHistoryEntry> buildVlmHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty())
            return List.of();
        List<VlmHistoryEntry> entries = new ArrayList<>(history.size());
        for (ChatMessage msg : history) {
            String imgUrl = msg.getImageUrl();
            if ("user".equals(msg.getRole()) && imgUrl != null && !imgUrl.isBlank()) {
                // Re-embed the image so the VLM retains visual context in follow-up turns
                try {
                    byte[] imgBytes = resolveImageBytes(imgUrl);
                    String b64 = java.util.Base64.getEncoder().encodeToString(imgBytes);
                    List<com.hades.services.service.sagemaker.dto.VlmContentPart> parts = List.of(
                            com.hades.services.service.sagemaker.dto.VlmContentPart.imageBase64(b64, "image/jpeg"),
                            com.hades.services.service.sagemaker.dto.VlmContentPart.text(msg.getContent()));
                    entries.add(VlmHistoryEntry.multimodal(msg.getRole(), parts));
                } catch (Exception e) {
                    log.warn("[VLM] Could not re-embed history image {}: {}", imgUrl, e.getMessage());
                    entries.add(VlmHistoryEntry.text(msg.getRole(), msg.getContent()));
                }
            } else {
                entries.add(VlmHistoryEntry.text(msg.getRole(), msg.getContent()));
            }
        }
        return entries;
    }

    private String buildSystemVeri(List<SageMakerYoloDetection> detections) {
        String detectionLines = detections.stream()
                .map(d -> String.format("- %s (güven: %.2f)", d.className(), d.confidence()))
                .collect(Collectors.joining("\n"));

        return "[SİSTEM VERİSİ]\n" +
                "Tespit edilen nesneler:\n" +
                detectionLines + "\n" +
                "[/SİSTEM VERİSİ]";
    }

    /**
     * Matches bounding boxes in VLM text to YOLO detections and enriches the
     * response.
     */
    private String matchAndEnrichDetections(
            String text,
            List<SageMakerYoloDetection> yoloDetections,
            List<SageMakerYoloDetection> matchedResults) {

        if (text == null || yoloDetections == null || yoloDetections.isEmpty())
            return text;

        // Strictly match: <ref>Label</ref><box>(y1, x1, y2, x2)</box> or [y1, x1, y2,
        // x2]
        // This ensures we only replace boxes that are explicitly referenced as objects.
        java.util.regex.Pattern boxPattern = java.util.regex.Pattern.compile(
                "<ref>(.*?)</ref>\\s*<box>\\s*\\(?(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)?\\s*</box>");

        java.util.regex.Matcher matcher = boxPattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            sb.append(text, lastEnd, matcher.start());

            String label = matcher.group(1);
            int x1 = Integer.parseInt(matcher.group(2));
            int y1 = Integer.parseInt(matcher.group(3));
            int x2 = Integer.parseInt(matcher.group(4));
            int y2 = Integer.parseInt(matcher.group(5));

            SageMakerYoloDetection bestMatch = findBestMatch(x1, y1, x2, y2, yoloDetections);

            if (bestMatch != null && bestMatch.maskRle() != null) {
                int index = matchedResults.size();
                matchedResults.add(bestMatch);
                // Keep the label but replace the box/ref tags with the segmentation marker
                sb.append("**").append(label).append("** [SEGMENTATION_").append(index).append("]");
            } else {
                // Keep original if no match
                sb.append(matcher.group());
            }
            lastEnd = matcher.end();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }

    private SageMakerYoloDetection findBestMatch(int x1, int y1, int x2, int y2,
            List<SageMakerYoloDetection> yoloDetections) {
        SageMakerYoloDetection best = null;
        double maxIoU = 0.3; // Minimum overlap threshold

        for (SageMakerYoloDetection det : yoloDetections) {
            List<Integer> yBox = det.bbox();
            if (yBox == null || yBox.size() < 4)
                continue;

            // IoU Calculation - assuming same scale (0-1000 or pixels)
            double iou = calculateIoU(x1, y1, x2, y2, yBox.get(0), yBox.get(1), yBox.get(2), yBox.get(3));
            if (iou > maxIoU) {
                maxIoU = iou;
                best = det;
            }
        }
        return best;
    }

    private double calculateIoU(int x1, int y1, int x2, int y2, int bx1, int by1, int bx2, int by2) {
        int interX1 = Math.max(x1, bx1);
        int interY1 = Math.max(y1, by1);
        int interX2 = Math.min(x2, bx2);
        int interY2 = Math.min(y2, by2);

        int interWidth = Math.max(0, interX2 - interX1);
        int interHeight = Math.max(0, interY2 - interY1);
        int interArea = interWidth * interHeight;

        int area1 = (x2 - x1) * (y2 - y1);
        int area2 = (bx2 - bx1) * (by2 - by1);

        return (double) interArea / (area1 + area2 - interArea);
    }
}
