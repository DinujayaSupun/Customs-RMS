package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.LoginRequest;
import lk.customs.rms.dto.LoginResponse;
import lk.customs.rms.dto.UserSummaryResponse;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserRepository userRepository,
                          CurrentUserService currentUserService,
                          AuditLogService auditLogService,
                          PermissionService permissionService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            Long actorId = userRepository.findByUsernameIgnoreCase(request.getUsername())
                .map(u -> u.getId())
                .orElse(0L);
            auditLogService.logEvent(
                "AUTH",
                actorId,
                "LOGIN_FAILED",
                actorId,
                "Login failed",
                "{\"username\":\"" + request.getUsername() + "\"}"
            );
            throw new BadRequestException("Invalid username or password.");
        }

        var user = userRepository.findByUsernameAndIsActiveTrue(request.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found."));

        String role = user.getRole() == null ? "USER" : user.getRole().getRoleName();
        String token = jwtService.generateToken(user.getId(), user.getUsername(), role);

        auditLogService.logEvent(
            "AUTH",
            user.getId(),
            "LOGIN_SUCCESS",
            user.getId(),
            "User logged in",
            "{\"username\":\"" + user.getUsername() + "\",\"role\":\"" + role + "\"}"
        );

        return LoginResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(role)
            .permissions(permissionService.permissionNamesForUser(user))
                .build();
    }

    @GetMapping("/me")
    public UserSummaryResponse me(Authentication authentication) {
        var u = currentUserService.requireUser(authentication);
        return UserSummaryResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .fullName(u.getFullName())
            .email(u.getEmail())
            .phone(u.getPhone())
            .department(u.getDepartment())
            .active(u.getIsActive())
                .role(u.getRole() == null ? null : u.getRole().getRoleName())
                .permissions(permissionService.permissionNamesForUser(u))
                .build();
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> users() {
        return userRepository.findByIsActiveTrueAndRole_RoleNameNotOrderByFullNameAsc("ADMIN").stream()
                .map(u -> UserSummaryResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .department(u.getDepartment())
                .active(u.getIsActive())
                        .role(u.getRole() == null ? null : u.getRole().getRoleName())
                        .permissions(permissionService.permissionNamesForUser(u))
                        .build())
                .toList();
    }
}
