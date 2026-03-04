package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private Long userId;
    private String username;
    private String fullName;
    private String role;
}
