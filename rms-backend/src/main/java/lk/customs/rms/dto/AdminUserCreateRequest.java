package lk.customs.rms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserCreateRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String username;

    @Email
    private String email;

    private String phone;

    private String department;

    @NotBlank
    private String role;

    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "Password must be at least 8 characters with letters and numbers.")
    private String password;
}
