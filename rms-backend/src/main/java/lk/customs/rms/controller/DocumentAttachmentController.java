package lk.customs.rms.controller;

import lk.customs.rms.dto.AttachmentResponse;
import lk.customs.rms.dto.DownloadUrlResponse;
import lk.customs.rms.entity.User;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.security.JwtService;
import lk.customs.rms.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@CrossOrigin
public class DocumentAttachmentController {

    private final AttachmentService attachmentService;
    private final CurrentUserService currentUserService;
    private final JwtService jwtService;

    public DocumentAttachmentController(AttachmentService attachmentService, CurrentUserService currentUserService, JwtService jwtService) {
        this.attachmentService = attachmentService;
        this.currentUserService = currentUserService;
        this.jwtService = jwtService;
    }

    // ✅ Upload attachment (versioned)
    // POST /api/documents/{documentId}/attachments?uploadedByUserId=1
    @PostMapping("/api/documents/{documentId}/attachments")
    public AttachmentResponse upload(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return attachmentService.upload(documentId, currentUserService.requireUserId(authentication), file);
    }

    // ✅ List attachments for a document
    // GET /api/documents/{documentId}/attachments
    @GetMapping("/api/documents/{documentId}/attachments")
    public List<AttachmentResponse> list(@PathVariable Long documentId, Authentication authentication) {
        return attachmentService.listForDocument(documentId, currentUserService.requireUserId(authentication));
    }

    // ✅ Download any attachment version
    // GET /api/attachments/{attachmentId}/download
    // Optional: ?performedByUserId=6 (audit)
    // ✅ NEW: ?inline=true (preview pdf/images in browser)
    @GetMapping("/api/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long attachmentId,
            @RequestParam(defaultValue = "false") boolean inline,
            Authentication authentication
    ) {
        var result = attachmentService.downloadWithMeta(attachmentId, currentUserService.requireUserId(authentication));

        String safeFileName = (result.fileName() == null || result.fileName().isBlank())
                ? ("attachment-" + attachmentId)
                : result.fileName();

        // RFC5987 filename* support (handles spaces + unicode)
        String encoded = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8).replace("+", "%20");

        // Detect content type (for preview)
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detected = Files.probeContentType(result.resource().getFile().toPath());
            if (detected != null && !detected.isBlank()) {
                mediaType = MediaType.parseMediaType(detected);
            }
        } catch (Exception ignored) {
            // fallback below
        }

        // If detection fails, use filename extensions for common preview types
        String lower = safeFileName.toLowerCase();
        if (mediaType.equals(MediaType.APPLICATION_OCTET_STREAM)) {
            if (lower.endsWith(".pdf")) mediaType = MediaType.APPLICATION_PDF;
            else if (lower.endsWith(".png")) mediaType = MediaType.IMAGE_PNG;
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mediaType = MediaType.IMAGE_JPEG;
            else if (lower.endsWith(".gif")) mediaType = MediaType.IMAGE_GIF;
        }

        boolean previewable =
                mediaType.equals(MediaType.APPLICATION_PDF) ||
                mediaType.toString().startsWith("image/");

        String dispositionType = (inline && previewable) ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        dispositionType + "; filename=\"" + safeFileName + "\"; filename*=UTF-8''" + encoded)
                .body(result.resource());
    }

    @PostMapping("/api/attachments/{attachmentId}/download-token")
    public DownloadUrlResponse createDownloadToken(
            @PathVariable Long attachmentId,
            @RequestParam(defaultValue = "false") boolean inline,
            Authentication authentication
    ) {
        User user = currentUserService.requireUser(authentication);
        String role = user.getRole() == null ? null : user.getRole().getRoleName();
        String token = jwtService.generateDownloadToken(user.getId(), user.getUsername(), role, "ATTACHMENT", attachmentId);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/attachments/{attachmentId}/download")
                .queryParam("download_token", token)
                .queryParam("inline", inline)
                .buildAndExpand(attachmentId)
                .toUriString();
        return DownloadUrlResponse.builder()
                .url(url)
                .expiresInSeconds(120)
                .build();
    }

    // ✅ Soft delete attachment version
    // DELETE /api/attachments/{attachmentId}?performedByUserId=1
    @DeleteMapping("/api/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long attachmentId,
            Authentication authentication
    ) {
        attachmentService.softDelete(attachmentId, currentUserService.requireUserId(authentication));
        return ResponseEntity.noContent().build();
    }
}
