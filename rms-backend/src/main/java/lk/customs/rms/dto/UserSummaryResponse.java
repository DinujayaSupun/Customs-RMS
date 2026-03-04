package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSummaryResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String department;
    private String role;
    private Boolean active;
}
