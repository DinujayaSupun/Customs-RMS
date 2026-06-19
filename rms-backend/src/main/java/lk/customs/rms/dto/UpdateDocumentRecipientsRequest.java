package lk.customs.rms.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateDocumentRecipientsRequest {
    @Size(max = 50, message = "CC recipients cannot exceed 50")
    private List<Long> ccUserIds;

    @Size(max = 50, message = "BCC recipients cannot exceed 50")
    private List<Long> bccUserIds;
}
