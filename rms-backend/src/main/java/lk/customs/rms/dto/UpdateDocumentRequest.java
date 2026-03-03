package lk.customs.rms.dto;

import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private Long performedByUserId;
}
