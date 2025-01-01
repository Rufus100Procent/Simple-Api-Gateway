package se.stykle.simple.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;

/*
JWT Validation – Check if the incoming request contains a valid JWT in the Authorization header and
extract necessary data from the token
 */
@Component
public class TokenHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(TokenHelper.class);

    public Mono<Boolean> isJwtTokenPresent(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            String authorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ") && authorizationHeader.length() > 7) {
                String token = authorizationHeader.substring(7).trim();
                if (!token.isEmpty()) {
                    logger.info("JWT Token found: {}", token);
                    exchange.getAttributes().put("jwtToken", token);
                    return Mono.just(false);  // Token is present
                }
            }

            logger.warn("No JWT Token found in the Authorization header.");
            return Mono.just(true);
        });
    }

    private Mono<Map<String, Object>> extractClaims(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            String token = (String) exchange.getAttributes().get("jwtToken");

            if (token != null) {
                try {
                    String[] parts = token.split("\\.");
                    if (parts.length > 1) {
                        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                        Map<String, Object> claims = objectMapper.readValue(
                                payload, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
                        );
                        return Mono.just(claims);
                    }
                } catch (Exception e) {
                    logger.error("Failed to decode JWT token", e);
                }
            }

            logger.warn("No JWT Token available to extract claims.");
            return Mono.empty();
        });
    }

    public Mono<Set<String>> extractCognitoGroups(ServerWebExchange exchange) {
        return extractClaims(exchange).flatMap(claims -> {
            Object groups = claims.get("cognito:groups");
            if (groups instanceof List<?> groupList) {
                Set<String> groupSet = new HashSet<>();
                for (Object group : groupList) {
                    if (group instanceof String) {
                        groupSet.add((String) group);
                    } else {
                        logger.warn("Unexpected group type: {}", group.getClass().getName());
                    }
                }
                logger.info("Cognito groups extracted: {}", groupSet);
                return Mono.just(groupSet);
            }
            return Mono.just(Collections.emptySet());
        });
    }

    public Mono<String> extractUsername(ServerWebExchange exchange) {
        return extractClaims(exchange).flatMap(claims -> {
            String username = (String) claims.getOrDefault("cognito:username", "unknown");
            if (!"unknown".equals(username)) {
                logger.info("Username extracted: {}", username);
            }
            return Mono.just(username);
        });
    }

    public Mono<String> extractUserPoolId(ServerWebExchange exchange) {
        return extractClaims(exchange).flatMap(claims -> {
            String iss = (String) claims.get("iss");
            if (iss != null && iss.contains("amazonaws.com/")) {
                String userPoolId = iss.substring(iss.lastIndexOf('/') + 1);
                logger.info("User Pool ID extracted: {}", userPoolId);
                return Mono.just(userPoolId);
            }
            logger.warn("User Pool ID not found in JWT token.");
            return Mono.just("unknown");
        });
    }

    public Mono<String> extractSub(ServerWebExchange exchange) {
        return extractClaims(exchange).flatMap(claims -> {
            String sub = (String) claims.getOrDefault("sub", "unknown");
            if (!"unknown".equals(sub)) {
                logger.info("Sub extracted: {}", sub);
            }
            return Mono.just(sub);
        });
    }

    public Mono<String> extractEmail(ServerWebExchange exchange) {
        return extractClaims(exchange).flatMap(claims -> {
            String email = (String) claims.getOrDefault("email", "unknown");
            if (!"unknown".equals(email)) {
                logger.info("Email extracted: {}", email);
            }
            return Mono.just(email);
        });
    }
}