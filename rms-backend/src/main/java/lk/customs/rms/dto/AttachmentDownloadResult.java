package lk.customs.rms.dto;

import org.springframework.core.io.Resource;

public record AttachmentDownloadResult(Resource resource, String fileName) {}
