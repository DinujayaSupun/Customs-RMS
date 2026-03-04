package lk.customs.rms.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DuplicateUserCandidateResponse {
    private String fullName;
    private String role;
    private List<AdminUserResponse> users;
}
