package org.sebastianDev.service;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.CreateReservationRequest;
import org.sebastianDev.ReservationResponse;
import org.sebastianDev.ReservationServiceGrpc;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@ApplicationScoped
public class BookingService {

    public void createBooking(UUID roomId, UUID userId, LocalDate checkIn, LocalDate checkOut) {
        // Cria o canal para a comunicação gRPC
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        // Cria o cliente gRPC
        ReservationServiceGrpc.ReservationServiceBlockingStub stub = ReservationServiceGrpc.newBlockingStub(channel);

        // Cria a requisição para o serviço de reserva
        CreateReservationRequest request = CreateReservationRequest.newBuilder()
                .setRoomId(roomId.toString())
                .setUserId(userId.toString())
                .setCheckInDate(Timestamp.newBuilder().setSeconds(checkIn.atStartOfDay().toEpochSecond(ZoneOffset.UTC)).build())
                .setCheckOutDate(Timestamp.newBuilder().setSeconds(checkOut.atStartOfDay().toEpochSecond(ZoneOffset.UTC)).build())
                .build();

        // Chama o método gRPC
        ReservationResponse response = stub.createReservation(request);

        // Processa a resposta
        System.out.println("Booking created: " + response.getBookingId());
    }
}
