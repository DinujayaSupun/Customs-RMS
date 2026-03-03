package lk.customs.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRemarkRequest {

    @NotNull
    private Long remarkedByUserId;

    @NotBlank
    private String remarkText;
}
