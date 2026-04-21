package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.ChangeMyPasswordRequest;
import lk.customs.rms.dto.LoginRequest;
import lk.customs.rms.dto.LoginResponse;
import lk.customs.rms.dto.UpdateMyProfileRequest;
import lk.customs.rms.dto.UserSummaryResponse;
import lk.customs.rms.entity.User;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.UserRepository;
import lk.customs.rms.service.AuditLogService;
import lk.customs.rms.service.FileStorageService;
import lk.customs.rms.service.PermissionService;
import lk.customs.rms.security.CurrentUserService;
import lk.customs.rms.security.JwtService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserRepository userRepository,
                          CurrentUserService currentUserService,
                          AuditLogService auditLogService,
                          PermissionService permissionService,
                          PasswordEncoder passwordEncoder,
                          FileStorageService fileStorageService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
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

        var user = userRepository.findByUsernameIgnoreCaseAndIsActiveTrue(request.getUsername())
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
                .hasProfilePicture(hasProfilePicture(user))
                .profilePictureUpdatedAt(user.getProfilePictureUpdatedAt())
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
                .hasProfilePicture(hasProfilePicture(u))
                .profilePictureUpdatedAt(u.getProfilePictureUpdatedAt())
                .permissions(permissionService.permissionNamesForUser(u))
                .build();
    }

    @PutMapping("/me")
    public UserSummaryResponse updateMe(@Valid @RequestBody UpdateMyProfileRequest request,
                                        Authentication authentication) {
        User user = currentUserService.requireUser(authentication);

        user.setFullName(normalizeRequired(request.getFullName(), "Full name is required."));
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setPhone(normalizeNullable(request.getPhone()));

        User saved = userRepository.save(user);

        auditLogService.logEvent(
                "USER",
                saved.getId(),
                "USER_PROFILE_UPDATE",
                saved.getId(),
                "User updated own profile",
                null
        );

        return UserSummaryResponse.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .department(saved.getDepartment())
                .active(saved.getIsActive())
                .role(saved.getRole() == null ? null : saved.getRole().getRoleName())
                .hasProfilePicture(hasProfilePicture(saved))
                .profilePictureUpdatedAt(saved.getProfilePictureUpdatedAt())
                .permissions(permissionService.permissionNamesForUser(saved))
                .build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangeMyPasswordRequest request,
                                                 Authentication authentication) {
        User user = currentUserService.requireUser(authentication);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password must match.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        auditLogService.logEvent(
                "USER",
                user.getId(),
                "USER_PASSWORD_CHANGE",
                user.getId(),
                "User changed own password",
                null
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserSummaryResponse uploadProfilePicture(@RequestParam("file") MultipartFile file,
                                                    Authentication authentication) {
        User user = currentUserService.requireUser(authentication);
        validateProfilePicture(file);

        String oldPath = user.getProfilePicturePath();
        String savedPath = fileStorageService.saveProfilePicture(user.getId(), file);

        user.setProfilePicturePath(savedPath);
        user.setProfilePictureContentType(file.getContentType());
        user.setProfilePictureUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        if (oldPath != null && !oldPath.isBlank() && !oldPath.equals(savedPath)) {
            fileStorageService.deleteIfExists(oldPath);
        }

        auditLogService.logEvent(
                "USER",
                saved.getId(),
                "USER_PROFILE_PICTURE_UPDATE",
                saved.getId(),
                "User updated profile picture",
                null
        );

        return UserSummaryResponse.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .department(saved.getDepartment())
                .active(saved.getIsActive())
                .role(saved.getRole() == null ? null : saved.getRole().getRoleName())
                .hasProfilePicture(hasProfilePicture(saved))
                .profilePictureUpdatedAt(saved.getProfilePictureUpdatedAt())
                .permissions(permissionService.permissionNamesForUser(saved))
                .build();
    }

    @GetMapping("/me/profile-picture")
    public ResponseEntity<Resource> getProfilePicture(Authentication authentication) {
        User user = currentUserService.requireUser(authentication);
        if (!hasProfilePicture(user)) {
            throw new BadRequestException("Profile picture not found.");
        }

        Resource resource = fileStorageService.loadAsResource(user.getProfilePicturePath());
        MediaType mediaType;
        try {
            mediaType = user.getProfilePictureContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(user.getProfilePictureContentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
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
                        .hasProfilePicture(hasProfilePicture(u))
                        .profilePictureUpdatedAt(u.getProfilePictureUpdatedAt())
                        .permissions(permissionService.permissionNamesForUser(u))
                        .build())
                .toList();
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private boolean hasProfilePicture(User user) {
        return user != null && user.getProfilePicturePath() != null && !user.getProfilePicturePath().isBlank();
    }

    private void validateProfilePicture(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Profile picture file is required.");
        }
        if (file.getSize() > 5L * 1024L * 1024L) {
            throw new BadRequestException("Profile picture must be 5MB or smaller.");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean allowedType = contentType.equals("image/jpeg")
                || contentType.equals("image/jpg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp");
        if (!allowedType) {
            throw new BadRequestException("Only JPG, PNG, or WEBP profile pictures are allowed.");
        }
    }
}
