package org.sebastiandev.trip.user.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.contracts.grpc.GetUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.GetUserProfileResponse;
import org.sebastiandev.trip.contracts.grpc.SearchUserProfilesRequest;
import org.sebastiandev.trip.contracts.grpc.SearchUserProfilesResponse;
import org.sebastiandev.trip.contracts.grpc.UpsertUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.UpsertUserProfileResponse;
import org.sebastiandev.trip.contracts.grpc.UserProfileService;
import org.sebastiandev.trip.contracts.grpc.UserProfileView;
import org.sebastiandev.trip.user.domain.UserProfile;
import org.sebastiandev.trip.user.messaging.OutboxService;
import org.sebastiandev.trip.user.repository.UserProfileRepository;

@GrpcService
public class UserProfileGrpcService implements UserProfileService {
    @Inject UserProfileRepository profiles;
    @Inject OutboxService outbox;

    @Override
    @WithSession
    public Uni<GetUserProfileResponse> getProfile(GetUserProfileRequest request) {
        Uni<UserProfile> profile;
        if (!request.getId().isBlank()) {
            try {
                profile = profiles.findById(UUID.fromString(request.getId()));
            } catch (IllegalArgumentException exception) {
                return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());
            }
        } else if (!request.getSubject().isBlank()) {
            profile = profiles.find("subject", request.getSubject()).firstResult();
        } else {
            return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());
        }
        return profile
                .onItem().ifNull().failWith(Status.NOT_FOUND.asRuntimeException())
                .map(value -> GetUserProfileResponse.newBuilder().setProfile(view(value)).build());
    }

    @Override
    public Uni<UpsertUserProfileResponse> upsertProfile(UpsertUserProfileRequest request) {
        if (request.getSubject().isBlank() || request.getEmail().isBlank()) {
            return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());
        }
        return Panache.withTransaction(() -> profiles.find("subject", request.getSubject()).firstResult()
                .chain(profile -> {
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    boolean fresh = profile == null;
                    UserProfile value = fresh ? new UserProfile() : profile;
                    if (fresh) {
                        value.id = UUID.nameUUIDFromBytes(request.getSubject()
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        value.subject = request.getSubject();
                        value.createdAt = now;
                    }
                    value.email = request.getEmail();
                    value.firstName = request.getFirstName();
                    value.lastName = request.getLastName();
                    value.preferences = request.getPreferencesJson().isBlank()
                            ? "{}" : request.getPreferencesJson();
                    value.updatedAt = now;
                    Uni<UserProfile> saved = fresh ? profiles.persist(value) : Uni.createFrom().item(value);
                    return saved.chain(savedProfile -> enqueue(savedProfile).replaceWith(savedProfile));
                }))
                .map(profile -> UpsertUserProfileResponse.newBuilder().setProfile(view(profile)).build());
    }

    @Override
    @WithSession
    public Uni<SearchUserProfilesResponse> searchProfiles(SearchUserProfilesRequest request) {
        int page = Math.max(0, request.getPage());
        int size = request.getSize() == 0 ? 20 : Math.min(50, Math.max(1, request.getSize()));
        String term = "%" + request.getQuery().trim().toLowerCase(Locale.ROOT) + "%";
        String filter = "lower(email) like ?1 or lower(firstName) like ?1 or lower(lastName) like ?1";
        Uni<List<UserProfile>> values = profiles.find(filter, Sort.ascending("firstName"), term)
                .page(Page.of(page, size)).list();
        Uni<Long> total = profiles.count(filter, term);
        return Uni.combine().all().unis(values, total).asTuple()
                .map(result -> SearchUserProfilesResponse.newBuilder()
                        .addAllProfiles(result.getItem1().stream().map(this::view).toList())
                        .setPage(page).setSize(size).setTotalElements(result.getItem2()).build());
    }

    private Uni<Void> enqueue(UserProfile profile) {
        EventPayloads.UserProfileChanged payload =
                new EventPayloads.UserProfileChanged(profile.id, profile.subject, profile.email);
        return outbox.enqueue(TopicNames.USER_PROFILE_CHANGED, profile.id, null, payload).replaceWithVoid();
    }

    private UserProfileView view(UserProfile profile) {
        return UserProfileView.newBuilder()
                .setId(profile.id.toString())
                .setSubject(profile.subject)
                .setEmail(profile.email)
                .setFirstName(profile.firstName == null ? "" : profile.firstName)
                .setLastName(profile.lastName == null ? "" : profile.lastName)
                .setPreferencesJson(profile.preferences)
                .build();
    }
}
