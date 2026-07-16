package org.sebastiandev.trip.gateway.security;
import jakarta.enterprise.context.RequestScoped; import jakarta.inject.Inject; import java.nio.charset.StandardCharsets; import java.util.UUID; import org.eclipse.microprofile.jwt.JsonWebToken; import io.quarkus.security.identity.SecurityIdentity;
@RequestScoped public class CurrentUser{@Inject JsonWebToken jwt;@Inject SecurityIdentity identity;public String subject(){return jwt.getSubject();}public UUID id(){return UUID.nameUUIDFromBytes(subject().getBytes(StandardCharsets.UTF_8));}public boolean admin(){return identity.hasRole("ADMIN");}}
