package org.sebastianDev;

import io.quarkus.grpc.GrpcService;

import io.smallrye.mutiny.Uni;

@GrpcService
public class HelloGrpcService implements HelloGrpc {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        System.out.println("Received request: " + request.getName());
        return Uni.createFrom().item("Hello " + request.getName() + "!")
                .map(msg -> HelloReply.newBuilder().setMessage(msg).build());
    }

}
