package com.docusense.backend.service;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SecurityFilterService {

    public String getUserDepartment() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "General";
        }
        Map<?, ?> details = (Map<?, ?>) authentication.getDetails();
        return details != null && details.containsKey("department") ? (String) details.get("department") : "General";
    }

    public String getUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "ROLE_USER";
        }
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .findFirst()
                .orElse("ROLE_USER");
    }

    // Constructs a secure pgvector filter expression based on the current authenticated user's context.
    public Filter.Expression getSecureFilterExpression() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User is not Authenticated");
        }

        String userDepartment = getUserDepartment();
        String userRole = getUserRole();

        // Build allowed roles list based on the security hierarchy
        List<String> allowedRoles = new ArrayList<>();
        allowedRoles.add("ROLE_USER");
        if ("ROLE_MANAGER".equals(userRole)) {
            allowedRoles.add("ROLE_MANAGER");
        } else if ("ROLE_ADMIN".equals(userRole)) {
            allowedRoles.add("ROLE_MANAGER");
            allowedRoles.add("ROLE_ADMIN");
        }
        
        // Format roles list for the SQL-like parser: e.g., ['ROLE_USER', 'ROLE_MANAGER']
        StringBuilder rolesBuilder = new StringBuilder("[");
        for (int i = 0; i < allowedRoles.size(); i++) {
            rolesBuilder.append("'").append(allowedRoles.get(i)).append("'");
            if (i < allowedRoles.size() - 1) {
                rolesBuilder.append(",");
            }
        }
        rolesBuilder.append("]");

        // Construct SQL-like filter expression
        // Rule: User must match department OR department is 'General' AND document role clearance is <= user's role
        String filterString = String.format(
                "(department_owner == 'General' or department_owner == '%s') and required_role in %s",
                userDepartment,
                rolesBuilder.toString()
        );
        return new FilterExpressionTextParser().parse(filterString);
    }
}