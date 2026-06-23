package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecipientSummaryResponse {
    private List<RecipientUserResponse> to;
    private List<RecipientUserResponse> cc;
    private List<RecipientUserResponse> bcc;
    private String compactText;
}
