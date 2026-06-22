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

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    public void register(AuthRequest authRequest) {
        if (userRepository.findByUsername(authRequest.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username is already taken.");
        }
        User user = User.builder().username(authRequest.getUsername())
                .password(passwordEncoder.encode(authRequest.getPassword()))
                .department(authRequest.getDepartment())
                .role(authRequest.getRole() != null ? authRequest.getRole() : "ROLE_USER")
                .build();
        userRepository.save(user);
    }

    public AuthResponse login(AuthRequest authRequest) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    authRequest.getUsername(),
                    authRequest.getPassword()));

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Incorrect username or password");
        }
        User user = userRepository.findByUsername(authRequest.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String generateToken = jwtTokenUtil.generateToken(
                user.getUsername(),
                user.getRole(),
                user.getDepartment());
        return new AuthResponse(generateToken);
    }
}
