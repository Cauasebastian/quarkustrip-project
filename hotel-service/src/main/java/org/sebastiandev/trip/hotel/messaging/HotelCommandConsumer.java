package org.sebastiandev.trip.hotel.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.sebastiandev.trip.contracts.event.*;
import org.sebastiandev.trip.hotel.domain.HotelReservation;
import org.sebastiandev.trip.hotel.repository.*;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class HotelCommandConsumer {
    @Inject ObjectMapper mapper; @Inject RoomRepository rooms; @Inject HotelReservationRepository reservations;
    @Inject InboxRepository inbox; @Inject OutboxService outbox;

    @Incoming("reserve-hotel") public Uni<Void> reserve(String json) {
        EventEnvelope event=EventSchemaValidator.decodeValidated(mapper,json); EventPayloads.ReservationRequested request=EventCodec.payload(mapper,event,EventPayloads.ReservationRequested.class);
        return process(event,()->reservations.find("bookingItemId",request.bookingItemId()).firstResult().chain(existing->existing==null?hold(request,event):publish(existing,event.eventId())));
    }

    private Uni<Void> hold(EventPayloads.ReservationRequested request, EventEnvelope event) {
        LocalDate checkIn; LocalDate checkOut;
        try { checkIn=LocalDate.parse(request.attributes().get("checkIn")); checkOut=LocalDate.parse(request.attributes().get("checkOut")); }
        catch(RuntimeException exception){return failure(request,event,"INVALID_DATES");}
        if(!checkOut.isAfter(checkIn)) return failure(request,event,"INVALID_DATE_RANGE");
        return rooms.findById(request.resourceId(),LockModeType.PESSIMISTIC_WRITE).chain(room->{
            if(room==null||!room.active) return failure(request,event,"ROOM_NOT_FOUND");
            return reservations.count("roomId = ?1 and status in (?2, ?3) and checkIn < ?4 and checkOut > ?5",request.resourceId(),HotelReservation.Status.HELD,HotelReservation.Status.CONFIRMED,checkOut,checkIn)
                    .chain(conflicts->{if(conflicts>0)return failure(request,event,"ROOM_UNAVAILABLE"); OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC); HotelReservation reservation=new HotelReservation();
                        reservation.id=UUID.randomUUID();reservation.bookingId=request.bookingId();reservation.bookingItemId=request.bookingItemId();reservation.userId=request.userId();reservation.roomId=request.resourceId();
                        reservation.checkIn=checkIn;reservation.checkOut=checkOut;reservation.status=HotelReservation.Status.HELD;reservation.amountMinor=Math.multiplyExact(ChronoUnit.DAYS.between(checkIn,checkOut),room.nightlyPriceMinor);
                        reservation.currency=room.currency;reservation.holdUntil=request.holdUntil();reservation.createdAt=now;reservation.updatedAt=now;
                        return reservations.persist(reservation).chain(()->publish(reservation,event.eventId()));});
        });
    }

    @Incoming("confirm-hotel") public Uni<Void> confirm(String json){
        EventEnvelope event=EventSchemaValidator.decodeValidated(mapper,json);EventPayloads.ReservationAction request=EventCodec.payload(mapper,event,EventPayloads.ReservationAction.class);
        return process(event,()->reservations.findById(request.reservationId()).chain(reservation->{
            if(reservation==null||!reservation.bookingItemId.equals(request.bookingItemId()))return raw(request.bookingId(),request.bookingItemId(),request.reservationId(),0,"XXX","FAILED","RESERVATION_NOT_FOUND",TopicNames.HOTEL_FAILED,event.eventId());
            if(reservation.status==HotelReservation.Status.CONFIRMED)return publish(reservation,event.eventId());
            if(reservation.status!=HotelReservation.Status.HELD||reservation.holdUntil.isBefore(OffsetDateTime.now(ZoneOffset.UTC)))return outcome(reservation,"FAILED","HOLD_EXPIRED",TopicNames.HOTEL_FAILED,event.eventId());
            reservation.status=HotelReservation.Status.CONFIRMED;reservation.updatedAt=OffsetDateTime.now(ZoneOffset.UTC);return publish(reservation,event.eventId());
        }));
    }

    @Incoming("cancel-hotel") public Uni<Void> cancel(String json){
        EventEnvelope event=EventSchemaValidator.decodeValidated(mapper,json);EventPayloads.ReservationAction request=EventCodec.payload(mapper,event,EventPayloads.ReservationAction.class);
        return process(event,()->reservations.findById(request.reservationId()).chain(reservation->{
            if(reservation==null)return raw(request.bookingId(),request.bookingItemId(),request.reservationId(),0,"XXX","CANCELLED",null,TopicNames.HOTEL_CANCELLED,event.eventId());
            reservation.status=HotelReservation.Status.CANCELLED;reservation.updatedAt=OffsetDateTime.now(ZoneOffset.UTC);return publish(reservation,event.eventId());
        }));
    }

    private Uni<Void> process(EventEnvelope event,Supplier<Uni<Void>> action){return Panache.withTransaction(()->inbox.findById(event.eventId()).chain(existing->{if(existing!=null)return Uni.createFrom().voidItem();return action.get().chain(()->{InboxEvent done=new InboxEvent();done.eventId=event.eventId();done.type=event.type();done.processedAt=OffsetDateTime.now(ZoneOffset.UTC);return inbox.persist(done).replaceWithVoid();});}));}
    private Uni<Void> failure(EventPayloads.ReservationRequested request,EventEnvelope event,String reason){return raw(request.bookingId(),request.bookingItemId(),null,0,request.currency(),"FAILED",reason,TopicNames.HOTEL_FAILED,event.eventId());}
    private Uni<Void> publish(HotelReservation reservation, UUID cause) {
        String topic = switch (reservation.status) {
            case HELD -> TopicNames.HOTEL_HELD;
            case CONFIRMED -> TopicNames.HOTEL_CONFIRMED;
            case CANCELLED, EXPIRED -> TopicNames.HOTEL_CANCELLED;
        };
        return outcome(reservation, reservation.status.name(), null, topic, cause);
    }
    private Uni<Void> outcome(HotelReservation reservation,String status,String reason,String topic,UUID cause){return raw(reservation.bookingId,reservation.bookingItemId,reservation.id,reservation.amountMinor,reservation.currency,status,reason,topic,cause);}
    private Uni<Void> raw(UUID bookingId,UUID itemId,UUID reservationId,long amount,String currency,String status,String reason,String topic,UUID cause){return outbox.enqueue(topic,bookingId,cause,new EventPayloads.ReservationOutcome(bookingId,itemId,reservationId,amount,currency,status,reason)).replaceWithVoid();}
}
