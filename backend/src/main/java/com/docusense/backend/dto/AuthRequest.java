package com.docusense.backend.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
    private String department;
    private String role;
}
