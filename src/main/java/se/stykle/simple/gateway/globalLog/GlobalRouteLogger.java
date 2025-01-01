package se.stykle.simple.gateway.globalLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import se.stykle.simple.gateway.TokenHelper;

import java.net.URI;
import java.util.Set;

/*
monitor and track incoming and outgoing HTTP traffic, enhancing observability and debugging
 */
@Component
public class GlobalRouteLogger implements GlobalFilter, Ordered {

    private final Logger logger = LoggerFactory.getLogger(GlobalRouteLogger.class);
    private final TokenHelper tokenHelper;

    private final String ANSI_RESET = "\u001B[0m";
    private final String RED_BOLD = "\033[1;31m";
    private final String YELLOW_BOLD = "\033[1;33m";
    private final String BLUE_BOLD = "\033[1;34m";

    public GlobalRouteLogger(TokenHelper tokenHelper) {
        this.tokenHelper = tokenHelper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final ServerHttpRequest request = exchange.getRequest();

        final Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        final String routeId = (route != null) ? route.getId() : "unknown";
        final String requestPath = request.getPath().pathWithinApplication().value();
        final String origin = request.getHeaders().getFirst("Origin");
        final HttpMethod method = request.getMethod();

        // Log the incoming request details
        logger.info("""
                {}----- Incoming Request -----{}
                {}Route ID: {}{}
                {}Request Path: {}{}
                {}Origin: {}{}
                {}Method used: {}{}
                """,
                RED_BOLD, ANSI_RESET, RED_BOLD, routeId, ANSI_RESET, RED_BOLD, requestPath, ANSI_RESET, RED_BOLD,
                (origin != null ? origin : "unknown"), ANSI_RESET, RED_BOLD, method, ANSI_RESET);

        // Proceed with the filter chain while logging user details if available
        return tokenHelper.isJwtTokenPresent(exchange)
                .flatMap(isMissing -> {
                    if (isMissing) {
                        logger.warn("{}--- Missing Token ---{}", YELLOW_BOLD, ANSI_RESET);
                        return chain.filter(exchange);
                    } else {
                        return extractAndLogUserDetails(exchange)
                                .then(chain.filter(exchange));
                    }
                })
                .doOnTerminate(() -> logResponseDetails(exchange))
                .doOnError(throwable -> {
                    logger.error("{}----- Error Occurred -----{}\n{}Error: {}{}",
                            RED_BOLD, ANSI_RESET, RED_BOLD, throwable.getMessage(), ANSI_RESET);
                    logResponseDetails(exchange);
                });
    }


    private Mono<Void> extractAndLogUserDetails(ServerWebExchange exchange) {
        return Mono.zip(
                tokenHelper.extractUsername(exchange),
                tokenHelper.extractCognitoGroups(exchange),
                tokenHelper.extractUserPoolId(exchange),
                tokenHelper.extractEmail(exchange),
                tokenHelper.extractSub(exchange)
        ).flatMap(tuple -> {
            String username = tuple.getT1();
            Set<String> userGroups = tuple.getT2();
            String userPoolId = tuple.getT3();
            String email = tuple.getT4();
            String sub = tuple.getT5();

            logger.info("""
                            {}--- User Authenticated ---{}
                            {}Username: {}{}
                            {}User Groups: {}{}
                            {}User Pool ID: {}{}
                            {}Email: {}{}
                            {}Sub: {}{}""",
                    BLUE_BOLD, ANSI_RESET,
                    BLUE_BOLD, username, ANSI_RESET,
                    BLUE_BOLD, userGroups, ANSI_RESET,
                    BLUE_BOLD, userPoolId, ANSI_RESET,
                    BLUE_BOLD, email, ANSI_RESET,
                    BLUE_BOLD, sub, ANSI_RESET);

            return Mono.empty();
        });
    }

    private void logResponseDetails(ServerWebExchange exchange) {
        final ServerHttpRequest request = exchange.getRequest();
        final ServerHttpResponse response = exchange.getResponse();

        final HttpMethod method = request.getMethod();
        final String requestPath = request.getPath().pathWithinApplication().value();
        final Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        final String routeId = (route != null) ? route.getId() : "unknown";

        final String statusCode = response.getStatusCode() != null ? response.getStatusCode().toString() : "Unknown";
        final URI resourceServer = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

        String GREEN_BOLD = "\033[1;32m";
        logger.info("""
                        {}----- Response Details -----{}
                        {}Route ID: {}{}
                        {}Request Path: {}{}
                        {}HTTP Method: {}{}
                        {}Resource Server Respond: {}{}
                        {}Response Status Code: {}{}
                        """,
                GREEN_BOLD, ANSI_RESET, GREEN_BOLD, routeId, ANSI_RESET, GREEN_BOLD, requestPath, ANSI_RESET,
                GREEN_BOLD, method, ANSI_RESET, GREEN_BOLD,
                (resourceServer != null ? resourceServer.toString() : "Unknown"), ANSI_RESET,
                GREEN_BOLD, statusCode, ANSI_RESET);
    }

    @Override
    public int getOrder() {
        return -1; // Ensure this filter runs before other filters
    }
}
