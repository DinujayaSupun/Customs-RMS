package lk.customs.rms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserUpdateRequest {

    @NotBlank
    private String fullName;

    @Email
    private String email;

    @Pattern(regexp = "^(?=(?:\\D*\\d){10,}).*$", message = "Phone must contain at least 10 digits.")
    private String phone;

    private String department;
}
