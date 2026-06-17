package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipientUserResponse {
    private Long userId;
    private String name;
    private String role;
    private boolean currentUser;
}
