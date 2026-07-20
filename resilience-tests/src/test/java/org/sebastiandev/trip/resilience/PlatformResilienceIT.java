package org.sebastiandev.trip.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformResilienceIT {
    private static final String JDBC_URL = "jdbc:postgresql://localhost:35432/resilience";
    private static final String TOXIPROXY_URL = "http://localhost:38474";
    private static final String DEPENDENCY_URL = "http://localhost:38666";
    private static final String KAFKA_PROXY = "localhost:38663";
    private static final String MONGO_PROXY = "mongodb://localhost:38667/resilience?directConnection=true";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(350)).build();

    @BeforeAll
    static void initializeInfrastructure() throws Exception {
        createProxy("kafka", "0.0.0.0:8663", "kafka:9092");
        createProxy("dependency", "0.0.0.0:8666", "dependency-stub:8080");
        createProxy("mongodb", "0.0.0.0:8667", "mongodb:27017");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS outbox_event (
                      event_id VARCHAR(64) PRIMARY KEY, payload TEXT NOT NULL, published BOOLEAN NOT NULL DEFAULT FALSE
                    );
                    CREATE TABLE IF NOT EXISTS inbox_event (
                      event_id VARCHAR(64) PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    );
                    CREATE TABLE IF NOT EXISTS resource_hold (
                      item_id VARCHAR(64) PRIMARY KEY, state VARCHAR(32) NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS saga_state (
                      booking_id VARCHAR(64) PRIMARY KEY, sequence_no INTEGER NOT NULL,
                      saga_status VARCHAR(32) NOT NULL, payment_state VARCHAR(32) NOT NULL
                    );
                    """);
        }
    }

    @BeforeEach
    void resetState() throws Exception {
        setProxy("kafka", true);
        setProxy("dependency", true);
        setProxy("mongodb", true);
        removeToxic("dependency", "latency");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE outbox_event, inbox_event, resource_hold, saga_state");
        }
    }

    @AfterEach
    void restoreNetwork() throws Exception {
        setProxy("kafka", true);
        setProxy("dependency", true);
        setProxy("mongodb", true);
        removeToxic("dependency", "latency");
    }

    @Test
    @Order(1)
    void paymentSlowOrOfflineDoesNotBecomeAnUnboundedRequest() throws Exception {
        addLatency("dependency", 1_200);
        assertThatThrownBy(() -> post("/payment", 250))
                .isInstanceOf(HttpTimeoutException.class);

        removeToxic("dependency", "latency");
        setProxy("dependency", false);
        assertThatThrownBy(() -> post("/payment", 500))
                .isInstanceOfAny(ConnectException.class, java.io.IOException.class);

        setProxy("dependency", true);
        assertThat(post("/payment", 1_000).statusCode()).isEqualTo(200);
    }

    @Test
    @Order(2)
    void failureAfterFlightHoldRollsBackTheTransactionalEffect() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO resource_hold(item_id, state) VALUES (?, 'HELD')")) {
                insert.setString(1, "flight-item-1");
                insert.executeUpdate();
                throw new SQLException("injected failure after hold");
            } catch (SQLException expected) {
                connection.rollback();
            }
        }
        assertThat(count("resource_hold")).isZero();
    }

    @Test
    @Order(3)
    void kafkaUnavailableAfterCommitLeavesTheOutboxRecoverable() throws Exception {
        var eventId = UUID.randomUUID().toString();
        execute("INSERT INTO outbox_event(event_id, payload) VALUES (?, ?)", eventId, "{\"type\":\"PaymentRequested\"}");
        setProxy("kafka", false);
        assertThatThrownBy(() -> publish(eventId))
                .isInstanceOfAny(ExecutionException.class, java.util.concurrent.TimeoutException.class);
        assertThat(booleanValue("SELECT published FROM outbox_event WHERE event_id = ?", eventId)).isFalse();

        setProxy("kafka", true);
        publish(eventId);
        execute("UPDATE outbox_event SET published = TRUE WHERE event_id = ?", eventId);
        assertThat(booleanValue("SELECT published FROM outbox_event WHERE event_id = ?", eventId)).isTrue();
    }

    @Test
    @Order(4)
    void duplicateAndOutOfOrderEventsDoNotRepeatOrRegressEffects() throws Exception {
        execute("INSERT INTO inbox_event(event_id) VALUES (?) ON CONFLICT DO NOTHING", "event-1");
        execute("INSERT INTO inbox_event(event_id) VALUES (?) ON CONFLICT DO NOTHING", "event-1");
        assertThat(count("inbox_event")).isEqualTo(1);

        execute("INSERT INTO saga_state VALUES (?, ?, ?, ?)", "booking-1", 0, "RESERVING", "NOT_REQUESTED");
        applyOrderedState("booking-1", 2, "CONFIRMING");
        applyOrderedState("booking-1", 1, "PAYMENT_PENDING");
        assertThat(integerValue("SELECT sequence_no FROM saga_state WHERE booking_id = ?", "booking-1")).isEqualTo(2);
        assertThat(stringValue("SELECT saga_status FROM saga_state WHERE booking_id = ?", "booking-1")).isEqualTo("CONFIRMING");
    }

    @Test
    @Order(5)
    void notificationUnavailableKeepsItsEventUntilMongoRecovers() throws Exception {
        var eventId = "notification-" + UUID.randomUUID();
        execute("INSERT INTO outbox_event(event_id, payload) VALUES (?, ?)", eventId, "{\"type\":\"BookingConfirmed\"}");
        setProxy("mongodb", false);
        assertThatThrownBy(() -> storeNotification(eventId)).isInstanceOf(MongoException.class);
        assertThat(booleanValue("SELECT published FROM outbox_event WHERE event_id = ?", eventId)).isFalse();

        setProxy("mongodb", true);
        storeNotification(eventId);
        execute("UPDATE outbox_event SET published = TRUE WHERE event_id = ?", eventId);
        assertThat(booleanValue("SELECT published FROM outbox_event WHERE event_id = ?", eventId)).isTrue();
    }

    @Test
    @Order(6)
    void rejectedRefundMovesTheSagaToManualReview() throws Exception {
        execute("INSERT INTO saga_state VALUES (?, ?, ?, ?)", "booking-refund", 4, "COMPENSATING", "REFUND_PENDING");
        var response = post("/refund", 1_000);
        assertThat(response.statusCode()).isEqualTo(409);
        execute("UPDATE saga_state SET saga_status = ?, payment_state = ? WHERE booking_id = ?",
                "MANUAL_REVIEW", "REFUND_FAILED", "booking-refund");
        assertThat(stringValue("SELECT payment_state FROM saga_state WHERE booking_id = ?", "booking-refund"))
                .isEqualTo("REFUND_FAILED");
    }

    @Test
    @Order(7)
    void timeoutDuringCompensationEscalatesInsteadOfFinishingTheSaga() throws Exception {
        execute("INSERT INTO saga_state VALUES (?, ?, ?, ?)", "booking-compensation", 5, "COMPENSATING", "REFUNDED");
        addLatency("dependency", 1_200);
        assertThatThrownBy(() -> post("/compensation", 250)).isInstanceOf(HttpTimeoutException.class);
        execute("UPDATE saga_state SET saga_status = ? WHERE booking_id = ?", "MANUAL_REVIEW", "booking-compensation");
        assertThat(stringValue("SELECT saga_status FROM saga_state WHERE booking_id = ?", "booking-compensation"))
                .isEqualTo("MANUAL_REVIEW");
    }

    @Test
    @Order(8)
    void onlyIdempotentQueriesRetryTransientGrpcFailures() {
        AtomicInteger queryAttempts = new AtomicInteger();
        String result = SelectiveRetry.query(() -> {
            if (queryAttempts.incrementAndGet() < 3) throw new TransientDependencyException();
            return "available";
        });
        assertThat(result).isEqualTo("available");
        assertThat(queryAttempts).hasValue(3);

        AtomicInteger invalidAttempts = new AtomicInteger();
        assertThatThrownBy(() -> SelectiveRetry.query(() -> {
            invalidAttempts.incrementAndGet();
            throw new InvalidRequestException();
        })).isInstanceOf(InvalidRequestException.class);
        assertThat(invalidAttempts).hasValue(1);

        AtomicInteger commandAttempts = new AtomicInteger();
        assertThatThrownBy(() -> SelectiveRetry.command(() -> {
            commandAttempts.incrementAndGet();
            throw new TransientDependencyException();
        })).isInstanceOf(TransientDependencyException.class);
        assertThat(commandAttempts).hasValue(1);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, "trip", "trip");
    }

    private static void createProxy(String name, String listen, String upstream) throws Exception {
        var response = sendJson("POST", TOXIPROXY_URL + "/proxies", Map.of(
                "name", name, "listen", listen, "upstream", upstream, "enabled", true));
        assertThat(response.statusCode()).isIn(201, 409);
    }

    private static void setProxy(String name, boolean enabled) throws Exception {
        var response = sendJson("POST", TOXIPROXY_URL + "/proxies/" + name, Map.of("enabled", enabled));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static void addLatency(String proxy, int milliseconds) throws Exception {
        var response = sendJson("POST", TOXIPROXY_URL + "/proxies/" + proxy + "/toxics", Map.of(
                "name", "latency", "type", "latency", "stream", "downstream", "toxicity", 1.0,
                "attributes", Map.of("latency", milliseconds, "jitter", 0)));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static void removeToxic(String proxy, String toxic) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(TOXIPROXY_URL + "/proxies/" + proxy + "/toxics/" + toxic))
                .DELETE().timeout(Duration.ofSeconds(2)).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isIn(204, 404);
    }

    private static HttpResponse<String> sendJson(String method, String url, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(3)).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, int timeoutMs) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(DEPENDENCY_URL + path))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs)).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void publish(String eventId) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_PROXY);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "500");
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "1000");
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "1000");
        properties.put(ProducerConfig.RETRIES_CONFIG, "0");
        try (var producer = new KafkaProducer<String, String>(properties)) {
            producer.send(new ProducerRecord<>("trip.resilience.events.v1", eventId, "{}"))
                    .get(2, TimeUnit.SECONDS);
        }
    }

    private static void storeNotification(String eventId) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO_PROXY))
                .applyToSocketSettings(builder -> builder.connectTimeout(500, TimeUnit.MILLISECONDS)
                        .readTimeout(500, TimeUnit.MILLISECONDS))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(700, TimeUnit.MILLISECONDS))
                .build();
        try (MongoClient client = MongoClients.create(settings)) {
            client.getDatabase("resilience").getCollection("notifications")
                    .insertOne(new Document("eventId", eventId));
        }
    }

    private static void applyOrderedState(String bookingId, int sequence, String status) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE saga_state SET sequence_no = ?, saga_status = ? WHERE booking_id = ? AND sequence_no < ?")) {
            statement.setInt(1, sequence);
            statement.setString(2, status);
            statement.setString(3, bookingId);
            statement.setInt(4, sequence);
            statement.executeUpdate();
        }
    }

    private static void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static boolean booleanValue(String sql, String id) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static int integerValue(String sql, String id) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static String stringValue(String sql, String id) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private interface Operation<T> {
        T run();
    }

    private static final class SelectiveRetry {
        static <T> T query(Operation<T> operation) {
            for (int attempt = 1; ; attempt++) {
                try {
                    return operation.run();
                } catch (TransientDependencyException exception) {
                    if (attempt == 3) throw exception;
                }
            }
        }

        static <T> T command(Operation<T> operation) {
            return operation.run();
        }
    }

    private static final class TransientDependencyException extends RuntimeException {
    }

    private static final class InvalidRequestException extends RuntimeException {
    }
}
