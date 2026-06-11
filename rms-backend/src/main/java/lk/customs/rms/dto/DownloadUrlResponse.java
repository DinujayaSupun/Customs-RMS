package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DownloadUrlResponse {
    private String url;
    private long expiresInSeconds;
}
