package lk.customs.rms.dto;

import lk.customs.rms.enums.Priority;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateDocumentRequest {

    private String title;
    private LocalDate receivedDate;
    private String companyName;
    private Priority priority;
}
