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

    // Constructs a secure pgvector filter expression based on the current authenticated user's context.

    public Filter.Expression getSecureFilterExpression() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User is not Authenticated");
        }

        // 1. Extract Department from the custom JWT claims stored in the authentication details map

        Map<?, ?> details = (Map<?, ?>) authentication.getDetails();
        String userDepartment = details != null
                && details.containsKey("department") ? (String) details.get("department") : "General";

        // 2. Extract Role (e.g., ROLE_USER, ROLE_ADMIN, ROLE_MANAGER)

        String userRole = authentication
                .getAuthorities()
                .stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .findFirst()
                .orElse("ROLE_USER");

        // 3. Build allowed roles list based on the security hierarchy

        List<String> allowedRoles = new ArrayList<>();
        allowedRoles.add("ROLE_USER");
        // All authenticated users can see standard files
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

        // 4. Construct SQL-like filter expression
        // Rule: User must match department OR department is 'General' AND document role clearance is <= user's role

        String filterString = String.format(
                "(department_owner == 'General' or department_owner == '%s') and required_role in %s",
                userDepartment,
                rolesBuilder.toString()
        );
        return new FilterExpressionTextParser().parse(filterString);
    }
}