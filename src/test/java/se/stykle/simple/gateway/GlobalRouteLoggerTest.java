package se.stykle.simple.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import se.stykle.simple.gateway.globalLog.GlobalRouteLogger;

import java.net.URI;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GlobalRouteLoggerTest {

    private GlobalRouteLogger globalRouteLogger;
    private GatewayFilterChain filterChain;
    private TokenHelper tokenHelper;

    @BeforeEach
    void setUp() {
        tokenHelper = mock(TokenHelper.class);
        globalRouteLogger = new GlobalRouteLogger(tokenHelper);
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private ServerWebExchange createExchange(String authorizationHeader, Route route) {
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("http://localhost/api/test"));
        if (authorizationHeader != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(requestBuilder.build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    @Test
    void shouldLogIncomingRequestAndAuthenticatedUser() {
        Route route = Route.async().id("testRoute").uri("http://localhost").predicate(exchange -> true).build();
        ServerWebExchange exchange = createExchange("Bearer valid-jwt-token", route);

        when(tokenHelper.isJwtTokenPresent(exchange)).thenReturn(Mono.just(false));
        when(tokenHelper.extractUsername(exchange)).thenReturn(Mono.just("testUser"));
        when(tokenHelper.extractCognitoGroups(exchange)).thenReturn(Mono.just(Set.of("Admin")));
        when(tokenHelper.extractUserPoolId(exchange)).thenReturn(Mono.just("userPoolId"));
        when(tokenHelper.extractEmail(exchange)).thenReturn(Mono.just("testEmail@example.com"));
        when(tokenHelper.extractSub(exchange)).thenReturn(Mono.just("testSub"));

        // Execute filter
        globalRouteLogger.filter(exchange, filterChain).block();

        // Verify that user details extraction methods were called
        verify(tokenHelper).extractUsername(exchange);
        verify(tokenHelper).extractCognitoGroups(exchange);
        verify(tokenHelper).extractUserPoolId(exchange);
        verify(tokenHelper).extractEmail(exchange);
        verify(tokenHelper).extractSub(exchange);

        // Verify chain filter was called
        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldHandleInvalidJwtToken() {
        Route route = Route.async().id("testRoute").uri("http://localhost").predicate(exchange -> true).build();
        ServerWebExchange exchange = createExchange("Bearer invalid-jwt-token", route);

        when(tokenHelper.isJwtTokenPresent(exchange)).thenReturn(Mono.just(true));

        // Execute filter
        globalRouteLogger.filter(exchange, filterChain).block();

        // Verify chain filter was called even with invalid token (logging should not block the chain)
        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldLogMissingToken() {
        Route route = Route.async().id("testRoute").uri("http://localhost").predicate(exchange -> true).build();
        ServerWebExchange exchange = createExchange(null, route);

        when(tokenHelper.isJwtTokenPresent(exchange)).thenReturn(Mono.just(true));

        // Execute filter
        globalRouteLogger.filter(exchange, filterChain).block();

        // Verify chain filter was called even with missing token (logging should not block the chain)
        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldLogError() {
        Route route = Route.async().id("testRoute").uri("http://localhost").predicate(exchange -> true).build();
        ServerWebExchange exchange = createExchange(null, route);

        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.error(new RuntimeException("Test Error")));
        when(tokenHelper.isJwtTokenPresent(exchange)).thenReturn(Mono.just(false));

        // Execute filter and handle error
        globalRouteLogger.filter(exchange, filterChain).onErrorResume(throwable -> Mono.empty()).block();

        // Verify chain filter was called despite an error occurring
        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldLogErrorWhenJwtIsMissing() {
        Route route = Route.async().id("testRoute").uri("http://localhost").predicate(exchange -> true).build();
        ServerWebExchange exchange = createExchange(null, route);

        when(tokenHelper.isJwtTokenPresent(exchange)).thenReturn(Mono.just(true));

        // Execute filter and handle error
        globalRouteLogger.filter(exchange, filterChain).onErrorResume(throwable -> Mono.empty()).block();

        // Verify chain filter was called even with missing token (logging should not block the chain)
        verify(filterChain).filter(exchange);
    }

    @Test
    void shouldLogResponseDetails() {
        Route route = Route.async().id("testRoute").uri("http://localhost").predicate(exchange -> true).build();
        ServerWebExchange exchange = createExchange("Bearer valid-jwt-token", route);

        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(tokenHelper.isJwtTokenPresent(exchange)).thenReturn(Mono.just(false));
        when(tokenHelper.extractUsername(exchange)).thenReturn(Mono.just("testUser"));
        when(tokenHelper.extractCognitoGroups(exchange)).thenReturn(Mono.just(Set.of("Admin")));
        when(tokenHelper.extractUserPoolId(exchange)).thenReturn(Mono.just("userPoolId"));
        when(tokenHelper.extractEmail(exchange)).thenReturn(Mono.just("testEmail@example.com"));
        when(tokenHelper.extractSub(exchange)).thenReturn(Mono.just("testSub"));

        // Execute filter
        globalRouteLogger.filter(exchange, filterChain).block();

        // Verify chain filter was called
        verify(filterChain).filter(exchange);
    }
}
