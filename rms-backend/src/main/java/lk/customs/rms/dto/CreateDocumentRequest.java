package lk.customs.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lk.customs.rms.enums.Priority;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateDocumentRequest {

    @NotBlank
    private String refNo;

    @NotBlank
    private String title;

    @NotNull
    private LocalDate receivedDate;

    private String companyName;

    @NotNull
    private Priority priority;

    @NotNull
    private Long currentOwnerUserId;
}
