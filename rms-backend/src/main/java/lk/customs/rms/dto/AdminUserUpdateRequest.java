package lk.customs.rms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserUpdateRequest {

    @NotBlank
    private String fullName;

    @Email
    private String email;

    private String phone;

    private String department;
}
