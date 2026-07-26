package org.sebastiandev.trip.booking.service;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class BookingPostgresTestResource implements QuarkusTestResourceLifecycleManager {
    private static final List<String> INCOMING_CHANNELS = List.of(
            "flight-held", "flight-failed", "flight-confirmed", "flight-cancelled",
            "hotel-held", "hotel-failed", "hotel-confirmed", "hotel-cancelled",
            "transport-held", "transport-failed", "transport-confirmed", "transport-cancelled",
            "payment-succeeded", "payment-failed", "payment-refunded", "payment-refund-failed",
            "saga-dlq");

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.8-alpine"))
                .withDatabaseName("booking")
                .withUsername("booking")
                .withPassword("booking");
        postgres.start();

        Map<String, String> properties = new HashMap<>();
        properties.put("quarkus.datasource.username", postgres.getUsername());
        properties.put("quarkus.datasource.password", postgres.getPassword());
        properties.put("quarkus.datasource.jdbc.url", postgres.getJdbcUrl());
        properties.put("quarkus.datasource.reactive.url", "postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName());
        properties.put("quarkus.scheduler.enabled", "false");
        properties.put("quarkus.otel.sdk.disabled", "true");
        properties.put("quarkus.messaging.health.enabled", "false");
        properties.put("mp.messaging.outgoing.outbox.connector", "smallrye-in-memory");
        INCOMING_CHANNELS.forEach(channel ->
                properties.put("mp.messaging.incoming." + channel + ".connector", "smallrye-in-memory"));
        return properties;
    }

    @Override
    public void stop() {
        if (postgres != null) postgres.stop();
    }
}
