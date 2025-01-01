package se.stykle.simple.gateway.api;


import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class RestApiController {
    private final WebClient webClient;

    public RestApiController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Value("${spring.security.oauth2.client.registration.client1.client-id}")
    private String clientIdProduction;

    @Value("${spring.security.oauth2.client.registration.client2_development.client-id}")
    private String clientIdForDev;

    @Value("${spring.security.oauth2.client.registration.client1.client-secret}")
    private String clientSecretProduction;

    @Value("${spring.security.oauth2.client.registration.client2_development.client-secret}")
    private String clientSecretForDev;

    @Value("${spring.security.oauth2.client.registration.client1.redirect-uri}")
    private String configuredRedirectUri;

    @Value("${spring.security.oauth2.client.registration.client2_development.redirect-uri}")
    private String configuredRedirectUriForDev;

    @Value("${spring.security.oauth2.client.provider.client1.token-uri}")
    private String tokenUri;

    @GetMapping("/get")
    public Mono<String> index(@AuthenticationPrincipal Jwt jwt) {
        return Mono.just("Hello World " + jwt.getSubject());
    }

    @PostMapping("/exchange-token")
    public Mono<ResponseEntity<Map<String, Object>>> exchangeToken(
            @RequestParam("code") String authorizationCode,
            @RequestParam("redirectUri") String incomingRedirectUri) {

        // Determine client ID and secret based on redirect URI
        String selectedClientId;
        String selectedClientSecret;

        if (configuredRedirectUri.equals(incomingRedirectUri)) {
            selectedClientId = clientIdProduction;
            selectedClientSecret = clientSecretProduction;
        } else if (configuredRedirectUriForDev.equals(incomingRedirectUri)) {
            selectedClientId = clientIdForDev;
            selectedClientSecret = clientSecretForDev;
        } else {
            return Mono.just(
                    ResponseEntity.badRequest().body(Map.of("error",
                            "IncomingRedirectUri doesn't match any configuredRedirectUri."))
            );
        }

        // Prepare the request body
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", selectedClientId);
        params.add("client_secret", selectedClientSecret);
        params.add("code", authorizationCode);
        params.add("redirect_uri", incomingRedirectUri);

        return webClient.post()
                .uri(tokenUri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(tokens -> {
                    if (tokens.containsKey("id_token") && tokens.containsKey("access_token") &&
                            tokens.containsKey("refresh_token")) {
                        String idToken = (String) tokens.get("id_token");
                        Map<String, Object> tokenClaims = decodeIdToken(idToken);

                        // Prepare the response
                        Map<String, Object> filteredResponse = new HashMap<>();
                        filteredResponse.put("id_token", tokens.get("id_token"));
                        filteredResponse.put("access_token", tokens.get("access_token"));
                        filteredResponse.put("refresh_token", tokens.get("refresh_token"));
                        filteredResponse.putAll(tokenClaims);

                        return Mono.just(ResponseEntity.ok(filteredResponse));
                    } else {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", "Missing tokens in the response from Cognito");
                        return Mono.just(ResponseEntity.status(500).body(errorResponse));
                    }
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Failed to process the request. Please try again later.");
                    return Mono.just(ResponseEntity.status(500).body(errorResponse));
                });
    }


    // Decode the ID token and extract claims
    private Map<String, Object> decodeIdToken(String idToken) {
        Map<String, Object> extractedClaims = new HashMap<>();

        try {
            DecodedJWT decodedJWT = JWT.decode(idToken);

            // Extract claims
            List<String> cognitoGroups = decodedJWT.getClaim("cognito:groups").asList(String.class);
            String username = decodedJWT.getClaim("cognito:username").asString();
            String email = decodedJWT.getClaim("email").asString();
            Date expiration = decodedJWT.getExpiresAt();

            // Format the expiration date to a readable string in Stockholm timezone
            String formattedExp = null;
            if (expiration != null) {
                Instant instant = expiration.toInstant();
                ZoneId stockholmZoneId = ZoneId.of("Europe/Stockholm");
                ZonedDateTime stockholmDateTime = instant.atZone(stockholmZoneId);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
                formattedExp = stockholmDateTime.format(formatter);
            }

            // Populate claims
            if (cognitoGroups != null && !cognitoGroups.isEmpty()) {
                extractedClaims.put("groups", cognitoGroups);
            }
            if (username != null) {
                extractedClaims.put("username", username);
            }
            if (email != null) {
                extractedClaims.put("email", email);
            }
            if (formattedExp != null) {
                extractedClaims.put("exp", formattedExp);
            }

        } catch (JWTDecodeException e) {
            extractedClaims.put("error", "Failed to decode JWT token: " + e.getMessage());
        }

        return extractedClaims;
    }
}

