package se.wacoco.nowleapigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {


    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder ) {
        return builder.routes()
                .route("Company", r -> r
                        .path("/api/company/all")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/company/all"))
                .route("Company", r -> r
                        .path("/api/company/id")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/company/id"))


                .route("file_uploading", r -> r
                        .path("/api/file/upload")
                        .filters(GatewayFilterSpec::tokenRelay)// pass the token to the resource server.
                        .uri("https://RESOURCE_SERVER-1/api/file/upload"))


                .route("name", r -> r
                        .path("/api/name/all")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/inventor/all"))
                .route("inventor", r -> r
                        .path("/api/name/id")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/inventor/id"))


                .route("profile", r -> r
                        .path("/api/portfolio/all")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/portfolio/all"))
                .route("profile", r -> r
                        .path("/api/portfolio/id")
                        .uri("https://RESOURCE_SERVER-1/api/portfolio/id"))


                .route("testing", r -> r
                        .path("/test/ping")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/test/ping"))

                .route("public-url", r -> r
                        .path("/public")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/public"))




                .route("Company", r -> r
                        .path("/api/v3/email/all-history")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/v3/email/all-history"))

                //for debugging, print data that is being passes in the header, get more detailed info
                .route("private", r -> r
                        .path("/private")
                        .filters(f -> f.tokenRelay().filter((exchange, chain) -> {
                            exchange.getRequest().getHeaders().forEach((key, value) -> {
                                System.out.println(key + ": " + value);
                            });
                            return chain.filter(exchange);
                        }))
                        .uri("https://RESOURCE_SERVER-1/private"))


                .route("test", r -> r
                        .path("/hello")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/private"))

                .route("email", r -> r
                        .path("/api/v3/email/send-email")
                        .filters(GatewayFilterSpec::tokenRelay)
                        .uri("https://RESOURCE_SERVER-1/api/v3/email/send-email"))



                .build();



    }
}
