package org.sebastiandev.trip.gateway.security;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.sebastiandev.trip.gateway.api.ApiError;

@ApplicationScoped
public class RateLimitFilter {
    @Inject ReactiveRedisDataSource redis;

    @ServerRequestFilter
    public Uni<Response> filter(ContainerRequestContext context) {
        String authorization = context.getHeaderString(HttpHeaders.AUTHORIZATION);
        String identity = authorization == null ? "anonymous" : Integer.toHexString(authorization.hashCode());
        String key = "rate:" + identity + ":" + (System.currentTimeMillis() / 60_000);
        ReactiveValueCommands<String, Long> values = redis.value(Long.class);
        return values.incr(key)
                .call(count -> count == 1 ? redis.key().expire(key, Duration.ofMinutes(2)).replaceWithVoid()
                        : Uni.createFrom().voidItem())
                .map(count -> count > 120
                        ? Response.status(429).header("Retry-After", "60")
                                .entity(ApiError.of("RATE_LIMIT_EXCEEDED", "rate limit exceeded")).build()
                        : null)
                .onFailure().recoverWithNull();
    }
}
