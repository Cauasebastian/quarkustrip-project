package org.sebastiandev.trip.gateway.api;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.sebastiandev.trip.contracts.grpc.GetUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.MutinyUserProfileServiceGrpc;
import org.sebastiandev.trip.contracts.grpc.UpsertUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.UserProfileView;

@Path("/api/v1/users/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {
    @Inject @GrpcClient("user") MutinyUserProfileServiceGrpc.MutinyUserProfileServiceStub users;
    @Inject JsonWebToken jwt;

    @GET
    public Uni<UserApiModels.Profile> get() {
        return users.getProfile(GetUserProfileRequest.newBuilder().setSubject(jwt.getSubject()).build())
                .map(result -> profile(result.getProfile()));
    }

    @PUT
    public Uni<UserApiModels.Profile> put(@Valid UserApiModels.UpdateProfile body) {
        return users.upsertProfile(UpsertUserProfileRequest.newBuilder().setSubject(jwt.getSubject())
                        .setEmail(body.email()).setFirstName(body.firstName() == null ? "" : body.firstName())
                        .setLastName(body.lastName() == null ? "" : body.lastName())
                        .setPreferencesJson(body.preferencesJson() == null ? "{}" : body.preferencesJson()).build())
                .map(result -> profile(result.getProfile()));
    }

    private UserApiModels.Profile profile(UserProfileView value) {
        return new UserApiModels.Profile(value.getId(), value.getSubject(), value.getEmail(),
                value.getFirstName(), value.getLastName(), value.getPreferencesJson());
    }
}
