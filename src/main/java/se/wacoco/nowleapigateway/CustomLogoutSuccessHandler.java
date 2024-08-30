package se.wacoco.nowleapigateway;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;


@Component
public class CustomLogoutSuccessHandler implements ServerLogoutSuccessHandler {

    private static final String COGNITO_LOGOUT_URL = "https://DOMAIN.auth.eu-north-1.amazoncognito.com/logout";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String LOGOUT_REDIRECT_URI = "http://CUSTOM_REDIRECT_AFTER_LOGOUT:3000";

    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
        URI logoutUri = UriComponentsBuilder.fromUriString(COGNITO_LOGOUT_URL)
                .queryParam("client_id", CLIENT_ID)
                .queryParam("logout_uri", LOGOUT_REDIRECT_URI)
                .build()
                .toUri();

        exchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getExchange().getResponse().getHeaders().setLocation(logoutUri);
        return exchange.getExchange().getResponse().setComplete();
    }
}

