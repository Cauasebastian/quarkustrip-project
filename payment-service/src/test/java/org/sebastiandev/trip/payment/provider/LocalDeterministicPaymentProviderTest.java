package org.sebastiandev.trip.payment.provider;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class LocalDeterministicPaymentProviderTest{
 private final LocalDeterministicPaymentProvider provider=new LocalDeterministicPaymentProvider();
 @Test void succeedsDeterministically(){var result=provider.charge("pm_test_success",1000,"BRL");assertTrue(result.successful());assertNotNull(result.transactionId());}
 @Test void rejectsKnownFailureReference(){var result=provider.charge("pm_test_failure",1000,"BRL");assertFalse(result.successful());assertEquals("PAYMENT_DECLINED",result.reason());}
 @Test void refundIsIdempotentAtAdapterBoundary(){var result=provider.refund("txn_1","pm_test_success");assertTrue(result.successful());assertEquals("txn_1",result.transactionId());}
}
