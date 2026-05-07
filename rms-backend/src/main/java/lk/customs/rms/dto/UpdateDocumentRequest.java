package lk.customs.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lk.customs.rms.enums.Priority;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateDocumentRequest {

    @NotBlank
    private String refNo;

    @NotBlank
    private String title;

    @NotBlank
    private String companyName;

    @NotNull
    private LocalDate receivedDate;

    @NotNull
    private Priority priority;
}
