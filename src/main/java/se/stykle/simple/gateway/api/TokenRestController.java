package se.stykle.simple.gateway.api;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import se.stykle.simple.gateway.service.TokenService;

import java.util.Map;

@RestController
@RequestMapping("/api/v0")
public class TokenRestController {

    private final TokenService tokenService;

    public TokenRestController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/exchange-token")
    public Mono<Map<String, Object>> exchangeToken(
            @RequestParam("code") String authorizationCode,
            @RequestParam("redirectUri") String incomingRedirectUri) {
        return tokenService.exchangeToken(authorizationCode, incomingRedirectUri);
    }

    @PostMapping("/dev/refresh")
    public Mono<ResponseEntity<String>> refreshDevToken(@RequestParam String refreshToken) {
        return tokenService.refreshDevToken(refreshToken)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/prod/refresh")
    public Mono<ResponseEntity<String>> refreshProdToken(@RequestParam String refreshToken) {
        return tokenService.refreshProdToken(refreshToken)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/dev/logout")
    public Mono<Void> logoutDev(ServerWebExchange exchange) {
        return tokenService.logoutDev(exchange);
    }

    @GetMapping("/prod/logout")
    public Mono<Void> logoutProd(ServerWebExchange exchange) {
        return tokenService.logoutProd(exchange);
    }

}

