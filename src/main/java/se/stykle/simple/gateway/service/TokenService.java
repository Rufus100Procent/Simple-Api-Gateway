package se.stykle.simple.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Service
public class TokenService {

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

    @Value("${cognito.logout-uri}")
    private String cognitoLogoutUrl;

    @Value("${logout.redirect.uri.for.dev}")
    private String logoutRedirectUriForDev;

    @Value("${logout.redirect.uri.for.prod}")
    private String logoutRedirectUriForProd;

    @Value("${cognito.domain}")
    private String cognitoDomain;
    private final WebClient webClient;

    public TokenService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<Map<String, Object>> exchangeToken(String authorizationCode, String incomingRedirectUri) {
        String selectedClientId = resolveClientId(incomingRedirectUri);
        String selectedClientSecret = resolveClientSecret(incomingRedirectUri);

        if (selectedClientId == null || selectedClientSecret == null) {
            return Mono.error(new IllegalArgumentException("Invalid redirect URI"));
        }

        MultiValueMap<String, String> params = createTokenRequestParams(
                selectedClientId, selectedClientSecret, authorizationCode, incomingRedirectUri);

        return fetchTokens(params);
    }

    private String resolveClientId(String redirectUri) {
        if (configuredRedirectUri.equals(redirectUri)) {
            return clientIdProduction;
        } else if (configuredRedirectUriForDev.equals(redirectUri)) {
            return clientIdForDev;
        }
        return null;
    }

    private String resolveClientSecret(String redirectUri) {
        if (configuredRedirectUri.equals(redirectUri)) {
            return clientSecretProduction;
        } else if (configuredRedirectUriForDev.equals(redirectUri)) {
            return clientSecretForDev;
        }
        return null;
    }

    private MultiValueMap<String, String> createTokenRequestParams(
            String clientId, String clientSecret, String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);
        return params;
    }

    private Mono<Map<String, Object>> fetchTokens(MultiValueMap<String, String> params) {
        return webClient.post()
                .uri(tokenUri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(e -> Mono.error(new RuntimeException("Failed to fetch tokens", e)));
    }


    public Mono<Void> logoutDev(ServerWebExchange exchange) {
        return performLogout(exchange, clientIdForDev, logoutRedirectUriForDev);
    }

    public Mono<Void> logoutProd(ServerWebExchange exchange) {
        return performLogout(exchange, clientIdProduction, logoutRedirectUriForProd);
    }

    private Mono<Void> performLogout(ServerWebExchange exchange, String clientId, String logoutRedirectUri) {
        String logoutUrl = String.format("%s?client_id=%s&logout_uri=%s",
                cognitoLogoutUrl, clientId, logoutRedirectUri);

        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(java.net.URI.create(logoutUrl));

        return exchange.getResponse().setComplete();
    }


    public Mono<String> refreshDevToken(String refreshToken) {
        return refreshToken(refreshToken, clientIdForDev, clientSecretForDev);
    }


    public Mono<String> refreshProdToken(String refreshToken) {
        return refreshToken(refreshToken, clientIdProduction, clientSecretProduction);
    }


    private Mono<String> refreshToken(String refreshToken, String clientId, String clientSecret) {
        String requestBody = String.format(
                "grant_type=refresh_token&client_id=%s&refresh_token=%s",
                clientId, refreshToken
        );

        WebClient.RequestHeadersSpec<?> requestSpec = webClient.post()
                .uri(cognitoDomain + "/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(requestBody);

        // If client secret exists, apply Basic Authentication
        if (clientSecret != null && !clientSecret.isEmpty()) {
            String authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes());
            requestSpec.header("Authorization", authHeader);
        }

        return requestSpec
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    System.out.println("failed to refresh token: " + e.getMessage());
                    return Mono.just("Failed to refresh token: " + e.getMessage());
                });
    }

}
