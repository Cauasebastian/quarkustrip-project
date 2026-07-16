package org.sebastiandev.trip.notification.domain; import io.quarkus.mongodb.panache.common.MongoEntity; import java.util.UUID; import org.bson.codecs.pojo.annotations.BsonId;
@MongoEntity(collection="user_contacts") public class UserContact{@BsonId public UUID userId;public String subject;public String email;}
