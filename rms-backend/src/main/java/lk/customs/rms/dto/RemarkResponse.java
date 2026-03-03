package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RemarkResponse {
    private Long id;
    private Long documentId;

    private String remarkText;

    private Long remarkedByUserId;
    private String remarkedByName;

    private LocalDateTime remarkedAt;
}
