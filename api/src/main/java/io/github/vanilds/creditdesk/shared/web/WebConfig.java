package io.github.vanilds.creditdesk.shared.web;

import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.security.ActorArgumentResolver;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "CreditDesk API", version = "0.2.0",
                description = """
                        Credit and debt-renegotiation proposals for bank analysts. All data is synthetic.

                        **Auth:** Keycloak access token (`Authorization: Bearer …`). Roles: `ANALYST`, `MANAGER` \
                        (includes analyst), `AUDITOR` (read-only).

                        **Optimistic locking:** reads return `ETag: "<version>"`; every change needs \
                        `If-Match: "<version>"` — missing → 428, stale → 412.

                        **Tracing:** send `X-Correlation-Id` or get one back in the response."""),
        // Relative server keeps the generated contract identical on every machine (no random test port in it)
        servers = @Server(url = "/", description = "Same origin"),
        security = @SecurityRequirement(name = "keycloak"))
@SecurityScheme(name = "keycloak", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Access token from Keycloak (realm creditdesk). See api/requests.http for how to get one.")
public class WebConfig implements WebMvcConfigurer {

    static {
        // Actor comes from the token, not from the request: hide it from the OpenAPI contract
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver());
    }
}
