package org.sebastianDev;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.sebastianDev.service.grpc.ReservationServiceImpl;
/*
public class GrpcServer {

    private Server server;

    public void start() throws Exception {
        server = ServerBuilder.forPort(9090)
                .addService(new ReservationServiceImpl()) // Adiciona o serviço de reservas
                .build()
                .start();

        System.out.println("Server started, listening on 9090");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down gRPC server");
            server.shutdown();
        }));

        server.awaitTermination();
    }

    public static void main(String[] args) throws Exception {
        new GrpcServer().start();
    }
}

 */
