package org.sebastiandev.trip.payment.provider;
import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID;
@ApplicationScoped public class LocalDeterministicPaymentProvider implements PaymentProvider{
 public Result charge(String methodRef,long amount,String currency){if("pm_test_failure".equals(methodRef))return new Result(false,null,"PAYMENT_DECLINED");return new Result(true,"txn_"+UUID.randomUUID(),null);}
 public Result refund(String transactionId,String methodRef){if("pm_test_refund_failure".equals(methodRef))return new Result(false,transactionId,"REFUND_REJECTED");return new Result(true,transactionId,null);}
}
