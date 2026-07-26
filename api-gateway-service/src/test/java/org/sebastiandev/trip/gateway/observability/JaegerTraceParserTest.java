package org.sebastiandev.trip.gateway.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class JaegerTraceParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JaegerTraceParser parser = new JaegerTraceParser();

    @Test
    void summarizesProtocolsStagesAndFailureSignals() throws Exception {
        var response = mapper.readTree("""
                {
                  "data": [{
                    "traceID": "c7e9f5ee2dbd6b6bc7107b4c1d8e9b55",
                    "processes": {
                      "p1": {"serviceName": "api-gateway-service"},
                      "p2": {"serviceName": "booking-service"},
                      "p3": {"serviceName": "flight-service"}
                    },
                    "spans": [
                      {"traceID":"c7e9f5ee2dbd6b6bc7107b4c1d8e9b55","spanID":"01","operationName":"POST /api/v1/bookings","processID":"p1","startTime":1000000,"duration":50000,
                       "tags":[{"key":"span.kind","value":"server"},{"key":"http.request.method","value":"POST"}],"references":[]},
                      {"traceID":"c7e9f5ee2dbd6b6bc7107b4c1d8e9b55","spanID":"02","operationName":"trip.v1.BookingCommandService/CreateBooking","processID":"p2","startTime":1010000,"duration":15000,
                       "tags":[{"key":"span.kind","value":"server"},{"key":"rpc.system","value":"grpc"}],"references":[{"refType":"CHILD_OF","spanID":"01"}]},
                      {"traceID":"c7e9f5ee2dbd6b6bc7107b4c1d8e9b55","spanID":"03","operationName":"outbox.publish","processID":"p2","startTime":1100000,"duration":10000,
                       "tags":[{"key":"event.id","value":"event-1"},{"key":"booking.id","value":"booking-1"},{"key":"outbox.attempt","value":2},{"key":"messaging.destination.name","value":"trip.flight.reserve-requested.v1"}],"references":[]},
                      {"traceID":"c7e9f5ee2dbd6b6bc7107b4c1d8e9b55","spanID":"04","operationName":"inbox.process","processID":"p3","startTime":1200000,"duration":20000,
                       "tags":[{"key":"event.id","value":"event-1"},{"key":"inbox.duplicate","value":false},{"key":"messaging.destination.name","value":"trip.flight.reserve-requested.v1"}],"references":[]},
                      {"traceID":"c7e9f5ee2dbd6b6bc7107b4c1d8e9b55","spanID":"05","operationName":"saga.transition","processID":"p2","startTime":2000000,"duration":1000,
                       "tags":[{"key":"saga.state","value":"PAYMENT_PENDING"}],"references":[]},
                      {"traceID":"c7e9f5ee2dbd6b6bc7107b4c1d8e9b55","spanID":"06","operationName":"messaging.dlq","processID":"p3","startTime":2100000,"duration":1000,
                       "tags":[{"key":"event.id","value":"event-2"},{"key":"error","value":true},{"key":"messaging.destination.name","value":"trip.flight.confirm-requested.v1.dlq"}],"references":[]}
                    ]
                  }]
                }
                """);

        var summary = parser.parse("booking-1", "c7e9f5ee2dbd6b6bc7107b4c1d8e9b55",
                "PAYMENT_PENDING", List.of("api-gateway-service", "booking-service", "flight-service"), response);

        assertTrue(summary.available());
        assertTrue(summary.complete());
        assertEquals(List.of("api-gateway-service", "booking-service", "flight-service"),
                summary.expectedSagaServices());
        assertEquals(1_101, summary.totalDurationMs());
        assertEquals(2, summary.stages().size());
        assertTrue(summary.communications().stream().anyMatch(value -> value.protocol().equals("REST")));
        assertTrue(summary.communications().stream().anyMatch(value -> value.protocol().equals("GRPC")));
        assertTrue(summary.communications().stream().anyMatch(value -> value.protocol().equals("KAFKA")));
        assertEquals(1, summary.signals().retryCount());
        assertEquals(1, summary.signals().dlqCount());
        assertEquals(1, summary.signals().failedSpanCount());
    }

    @Test
    void combinesRelatedTracesAndReportsMissingSagaServices() throws Exception {
        var primary = mapper.readTree("""
                {"data":[{"traceID":"trace-primary","processes":{
                  "p1":{"serviceName":"api-gateway-service"},
                  "p2":{"serviceName":"booking-service"},
                  "p3":{"serviceName":"flight-service"},
                  "p4":{"serviceName":"payment-service"}},
                  "spans":[
                    {"traceID":"trace-primary","spanID":"1","operationName":"POST /api/v1/bookings","processID":"p1","startTime":1000000,"duration":1000,"tags":[],"references":[]},
                    {"traceID":"trace-primary","spanID":"2","operationName":"saga.transition","processID":"p2","startTime":1100000,"duration":1000,"tags":[{"key":"saga.state","value":"CONFIRMING"}],"references":[]},
                    {"traceID":"trace-primary","spanID":"3","operationName":"reservation.confirm","processID":"p3","startTime":1200000,"duration":1000,"tags":[],"references":[]},
                    {"traceID":"trace-primary","spanID":"4","operationName":"payment.charge","processID":"p4","startTime":1300000,"duration":1000,"tags":[],"references":[]}
                  ]}]}
                """);
        var related = mapper.readTree("""
                {"data":[{"traceID":"trace-cancel","processes":{
                  "p1":{"serviceName":"booking-service"},
                  "p2":{"serviceName":"notification-service"}},
                  "spans":[
                    {"traceID":"trace-cancel","spanID":"5","operationName":"saga.compensate","processID":"p1","startTime":1400000,"duration":1000,"tags":[],"references":[]},
                    {"traceID":"trace-cancel","spanID":"6","operationName":"notification.send","processID":"p2","startTime":1500000,"duration":1000,"tags":[{"key":"notification.outcome","value":"SENT"}],"references":[]}
                  ]}]}
                """);

        var summary = parser.parse("booking-1", "trace-primary", "COMPENSATING",
                List.of("api-gateway-service", "booking-service", "flight-service", "hotel-service",
                        "transport-service", "payment-service"),
                primary, related);

        assertFalse(summary.complete());
        assertEquals(List.of("hotel-service", "transport-service"), summary.missingServices());
        assertTrue(summary.observedServices().contains("notification-service"));
        assertEquals(List.of("trace-primary", "trace-cancel"), summary.traceIds());
        assertTrue(summary.signals().compensationStarted());
        assertEquals("SENT", summary.signals().notificationStatus());
    }

    @Test
    void returnsControlledUnavailableSummaryWithoutTraceData() {
        var summary = parser.parse("booking-1", "trace-1", "CONFIRMED", List.of());

        assertFalse(summary.available());
        assertEquals("TRACE_NOT_FOUND", summary.unavailableReason());
    }
}
