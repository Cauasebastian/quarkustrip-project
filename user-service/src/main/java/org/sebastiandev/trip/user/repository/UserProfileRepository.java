package org.sebastiandev.trip.user.repository; import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.user.domain.UserProfile;
@ApplicationScoped public class UserProfileRepository implements PanacheRepositoryBase<UserProfile,UUID>{}
