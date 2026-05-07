package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditLogPerformerOptionResponse {
    private Long id;
    private String name;
}
