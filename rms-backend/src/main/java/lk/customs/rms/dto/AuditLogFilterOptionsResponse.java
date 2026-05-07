package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuditLogFilterOptionsResponse {
    private List<String> actionTypes;
    private List<AuditLogPerformerOptionResponse> performers;
}
