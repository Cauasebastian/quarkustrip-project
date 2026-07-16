package org.sebastiandev.trip.payment.repository; import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.payment.domain.Payment;
@ApplicationScoped public class PaymentRepository implements PanacheRepositoryBase<Payment,UUID>{}
