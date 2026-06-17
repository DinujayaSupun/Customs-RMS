package lk.customs.rms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateDocumentRecipientsRequest {
    private List<Long> ccUserIds;
    private List<Long> bccUserIds;
}
