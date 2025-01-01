package se.stykle.simple.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import se.stykle.simple.gateway.TokenHelper;
import se.stykle.simple.gateway.handler.ResponseHandler;

@Configuration
public class GatewayConfig {

    private final Logger logger = LoggerFactory.getLogger(GatewayConfig.class);
    private final TokenHelper tokenHelper;

    public GatewayConfig(TokenHelper tokenHelper) {
        this.tokenHelper = tokenHelper;
    }

    @Value("${taget.micro.server.once.address}")
    private String tagetMicroServerOneAddress;

    @Value("${taget.micro.server.two.address}")
    private String tagetMicroServerTwoAddress;

    @Value("${taget.micro.server.tree.address}")
    private String tagetMicroServerTreeAddress;

    @Bean
    public RouterFunction<ServerResponse> fallbackRoute() {
        return RouterFunctions.route(
                RequestPredicates.path("/fallback"),
                request -> {
                    logger.info("Circuit Breaker Triggered: Fallback Executed");
                    return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(BodyInserters.fromValue("{\"error\": \"Resource server down or temporarily down. " +
                                    "Please try again later. If the issue keeps happening, contact admin\"}"));
                }
        );
    }

    private GatewayFilterSpec applyCommonFilters(GatewayFilterSpec f, String requiredGroup) {
        return f.filter((exchange, chain) ->
                tokenHelper.isJwtTokenPresent(exchange)
                        .flatMap(isMissing -> {
                            if (isMissing) {
                                return ResponseHandler.handleMissingToken(exchange);
                            }
                            return tokenHelper.extractCognitoGroups(exchange)
                                    .flatMap(userGroups -> {
                                        if (requiredGroup != null && !userGroups.contains(requiredGroup)) {
                                            return ResponseHandler.handleUnauthorizedAccess(exchange);
                                        }
                                        ServerWebExchange mutatedExchange = exchange.mutate()
                                                .request(builder -> builder.headers(httpHeaders ->
                                                        httpHeaders.remove("Authorization")))
                                                .build();
                                        return chain.filter(mutatedExchange);
                                    });
                        })
        );
    }

    private GatewayFilter addUserDetailsHeaders() {
        return (exchange, chain) -> {
            Mono<String> extractUserPoolId = tokenHelper.extractUserPoolId(exchange);
            Mono<String> extractUsername = tokenHelper.extractUsername(exchange);
            Mono<String> extractEmail = tokenHelper.extractEmail(exchange);
            Mono<String> extractSub = tokenHelper.extractSub(exchange);

            return Mono.zip(extractUserPoolId, extractUsername, extractEmail, extractSub)
                    .flatMap(tuple -> {
                        String userPoolId = tuple.getT1();
                        String username = tuple.getT2();
                        String email = tuple.getT3();
                        String sub = tuple.getT4();

                        exchange.getRequest().mutate()
                                .header("X-Cognito-UserPoolId", userPoolId)
                                .header("X-Username", username)
                                .header("X-Email", email)
                                .header("X-Sub", sub)
                                .build();

                        logger.info("Added headers: X-Cognito-UserPoolId={}, X-Username={}, X-Email={}, X-Sub={}",
                                userPoolId, username, email, sub);
                        return chain.filter(exchange);
                    })
                    .onErrorResume(e -> {
                        logger.warn("Error extracting user details from JWT: {}", e.getMessage());
                        return chain.filter(exchange);
                    });
        };
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                /*               Company                 */

                .route("Company_get_by_id_for_Prod", r -> r
                        .path("/api/v1/prod/company/{id}").and().method("GET")
                        .filters(f -> applyCommonFilters(f, "Admin") //only admin is allowed to call tagetMicroServerTwoAddress
                                .rewritePath("/api/v1/dev/company/(?<id>.*)", //incoming path
                                        "/api/v1/company/${id}")   //replacement path
                                .filter(addUserDetailsHeaders()) //add custom headers
                                .circuitBreaker(c -> c.setName("isServerSut-down-CircuitBreaker")
                                        .setFallbackUri("forward:/fallback")))
                        .uri(tagetMicroServerTwoAddress))

                .route("Company_get_by_id_for_dev", r -> r
                        .path("/api/v1/dev/company/{id}").and().method("GET")
                        .filters(f -> applyCommonFilters(f, "Admin")
                                .rewritePath("/api/v1/dev/company/(?<id>.*)", //incoming path
                                "/api/v1/company/${id}")   //replacement path
                                .filter(addUserDetailsHeaders())
                                .circuitBreaker(c -> c.setName("isServerSut-down-CircuitBreaker")
                                        .setFallbackUri("forward:/fallback")))
                        .uri(tagetMicroServerOneAddress))


                .route("Company_list_all_for_prod", r -> r
                        .path("/api/v1/prod/admin/companies").and().method("GET")
                        .filters(f -> applyCommonFilters(f, "Admin")
                                .rewritePath("/api/v1/dev/companies", //incoming path
                                "/api/v1/companies")   //replacement path
                                .filter(addUserDetailsHeaders())
                                .circuitBreaker(c -> c.setName("isServerSut-down-CircuitBreaker")
                                        .setFallbackUri("forward:/fallback")))
                        .uri(tagetMicroServerTwoAddress))


                .route("Company_list_all_for_dev", r -> r
                        .path("/api/v1/dev/companies").and().method("GET")
                        .filters(f -> applyCommonFilters(f, "Admin")
                                .rewritePath("/api/v1/dev/companies", //incoming path
                                "/api/v1/companies")   //replacement path
                                .filter(addUserDetailsHeaders())
                                .circuitBreaker(c -> c.setName("isServerSut-down-CircuitBreaker")
                                        .setFallbackUri("forward:/fallback")))
                        .uri(tagetMicroServerOneAddress))




                //for debugging, print data that is being passes in the header, get more detailed info
                .route("private", r -> r
                        .path("/private")
                        .filters(f -> f.tokenRelay().filter((exchange,
                                                             chain) -> {
                            exchange.getRequest().getHeaders().forEach((key, value) ->
                                    System.out.println(key + ": " + value));
                            return chain.filter(exchange);
                        }))
                        .uri("https://RESOURCE_SERVER-1/private"))


                .route("test", r -> r
                        .path("/hello")
                            .filters(f-> applyCommonFilters(f, "Default_Group")
                                .circuitBreaker(c -> c.setName("is-Server-Down--CircuitBreaker")
                                        .setFallbackUri("forward:/fallback"))
                                .tokenRelay())
                        .uri(tagetMicroServerTreeAddress))

                .route("email", r -> r
                        .path("/api/v4/email/send-email").and().method("POST")
                            .filters(f->f
                                .circuitBreaker(c -> c.setName("is-Server-Down--CircuitBreaker")
                                        .setFallbackUri("forward:/fallback"))
                                .tokenRelay())
                        .uri(tagetMicroServerTreeAddress))



                .build();
    }

}
