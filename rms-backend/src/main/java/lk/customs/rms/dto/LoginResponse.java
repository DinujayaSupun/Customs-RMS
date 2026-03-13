package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private Long userId;
    private String username;
    private String fullName;
    private String role;
    private List<String> permissions;
}
