package lk.customs.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lk.customs.rms.enums.Priority;
import lombok.Getter;
import lombok.Setter;

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
    private Priority priority;
}
