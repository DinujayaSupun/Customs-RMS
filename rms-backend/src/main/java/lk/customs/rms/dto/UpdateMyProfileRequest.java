package lk.customs.rms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMyProfileRequest {

    @NotBlank(message = "Full name is required.")
    @Size(max = 150, message = "Full name must be at most 150 characters.")
    private String fullName;

    @Email(message = "Email must be valid.")
    @Size(max = 150, message = "Email must be at most 150 characters.")
    private String email;

    @Pattern(regexp = "^(?=(?:\\D*\\d){10,}).*$", message = "Phone must contain at least 10 digits.")
    @Size(max = 30, message = "Phone must be at most 30 characters.")
    private String phone;
}
