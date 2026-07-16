package org.sebastiandev.trip.payment.domain;
import jakarta.persistence.*; import java.time.OffsetDateTime; import java.util.UUID;
@Entity @Table(name="payments") public class Payment{
 public enum Status{PENDING,SUCCEEDED,FAILED,REFUNDED,REFUND_FAILED} @Id public UUID id; @Column(name="booking_id",nullable=false,unique=true) public UUID bookingId;
 @Column(name="user_id",nullable=false) public UUID userId; @Column(name="amount_minor",nullable=false) public long amountMinor; @Column(nullable=false,length=3) public String currency;
 @Column(name="payment_method_ref",nullable=false) public String paymentMethodRef; @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
 @Column(name="transaction_id") public String transactionId; @Column(name="failure_reason") public String failureReason; @Column(name="created_at",nullable=false) public OffsetDateTime createdAt;
 @Column(name="updated_at",nullable=false) public OffsetDateTime updatedAt; @Version public long version;
}
