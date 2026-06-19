package lk.customs.rms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserCreateRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @Pattern(regexp = "^(?=(?:\\D*\\d){10,}).*$", message = "Phone must contain at least 10 digits.")
    private String phone;

    @Size(max = 120, message = "Department must be at most 120 characters")
    private String department;

    @NotBlank
    private String role;

    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "Password must be at least 8 characters with letters and numbers.")
    private String password;
}
