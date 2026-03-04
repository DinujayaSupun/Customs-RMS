package lk.customs.rms.dto;

import lk.customs.rms.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserResponse {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private String department;
    private String role;
    private Boolean active;
    private LocalDateTime createdAt;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .department(user.getDepartment())
                .role(user.getRole() == null ? null : user.getRole().getRoleName())
                .active(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
