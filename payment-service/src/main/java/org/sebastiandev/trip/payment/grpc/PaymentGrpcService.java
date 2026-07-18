package org.sebastiandev.trip.payment.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.GetPaymentRequest;
import org.sebastiandev.trip.contracts.grpc.GetPaymentResponse;
import org.sebastiandev.trip.contracts.grpc.Money;
import org.sebastiandev.trip.contracts.grpc.PaymentQueryService;
import org.sebastiandev.trip.contracts.grpc.PaymentView;
import org.sebastiandev.trip.payment.service.PaymentApplicationService;

@GrpcService
public class PaymentGrpcService implements PaymentQueryService {
    @Inject PaymentApplicationService service;

    @Override
    public Uni<GetPaymentResponse> getPayment(GetPaymentRequest request) {
        try {
            return service.getByBookingId(UUID.fromString(request.getBookingId()))
                    .onItem().ifNull().failWith(Status.NOT_FOUND.asRuntimeException())
                    .map(payment -> GetPaymentResponse.newBuilder().setPayment(PaymentView.newBuilder()
                            .setId(payment.id.toString())
                            .setBookingId(payment.bookingId.toString())
                            .setStatus(payment.status.name())
                            .setAmount(Money.newBuilder().setCurrency(payment.currency)
                                    .setAmountMinor(payment.amountMinor))
                            .setTransactionId(payment.transactionId == null ? "" : payment.transactionId)).build());
        } catch (IllegalArgumentException exception) {
            return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());
        }
    }
}
