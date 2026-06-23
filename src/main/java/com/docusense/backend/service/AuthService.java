package com.docusense.backend.service;

import com.docusense.backend.dto.AuthRequest;
import com.docusense.backend.dto.AuthResponse;
import com.docusense.backend.model.User;
import com.docusense.backend.repository.UserRepository;
import com.docusense.backend.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    public void register(AuthRequest authRequest) {
        if (authRequest.getUsername() == null || authRequest.getUsername().trim().isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (authRequest.getPassword() == null || authRequest.getPassword().trim().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        if (authRequest.getDepartment() == null || authRequest.getDepartment().trim().isBlank()) {
            throw new IllegalArgumentException("Department cannot be empty.");
        }
        
        // Validate department constraints
        String dept = authRequest.getDepartment().trim();
        List<String> allowedDepts = List.of("Engineering", "HR", "Finance", "General");
        if (allowedDepts.stream().noneMatch(d -> d.equalsIgnoreCase(dept))) {
            throw new IllegalArgumentException("Invalid department. Allowed: Engineering, HR, Finance, General");
        }

        if (userRepository.findByUsername(authRequest.getUsername().trim()).isPresent()) {
            throw new IllegalArgumentException("Username is already taken.");
        }
        
        // Strictly force ROLE_USER on signup to prevent privilege escalation
        User user = User.builder()
                .username(authRequest.getUsername().trim())
                .password(passwordEncoder.encode(authRequest.getPassword()))
                .department(dept)
                .role("ROLE_USER")
                .build();
        userRepository.save(user);
    }

    public AuthResponse login(AuthRequest authRequest) {
        // 1. Check if the user is registered first
        User user = userRepository.findByUsername(authRequest.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist. Please register first."));

        // 2. Validate credentials
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    authRequest.getUsername(),
                    authRequest.getPassword()));
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Incorrect password. Please try again.");
        }

        String generateToken = jwtTokenUtil.generateToken(
                user.getUsername(),
                user.getRole(),
                user.getDepartment());
        return new AuthResponse(generateToken);
    }
}
