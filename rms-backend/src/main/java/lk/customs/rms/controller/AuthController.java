package lk.customs.rms.controller;

import jakarta.validation.Valid;
import lk.customs.rms.dto.LoginRequest;
import lk.customs.rms.dto.LoginResponse;
import lk.customs.rms.dto.UserSummaryResponse;
import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.repository.UserRepository;
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

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserRepository userRepository,
                          CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("Invalid username or password.");
        }

        var user = userRepository.findByUsernameAndIsActiveTrue(request.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found."));

        String role = user.getRole() == null ? "USER" : user.getRole().getRoleName();
        String token = jwtService.generateToken(user.getId(), user.getUsername(), role);

        return LoginResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(role)
                .build();
    }

    @GetMapping("/me")
    public UserSummaryResponse me(Authentication authentication) {
        var u = currentUserService.requireUser(authentication);
        return UserSummaryResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .role(u.getRole() == null ? null : u.getRole().getRoleName())
                .build();
    }

    @GetMapping("/users")
    public List<UserSummaryResponse> users() {
        return userRepository.findByIsActiveTrueOrderByFullNameAsc().stream()
                .map(u -> UserSummaryResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .role(u.getRole() == null ? null : u.getRole().getRoleName())
                        .build())
                .toList();
    }
}
