package org.sebastiandev.trip.notification.domain;
import io.quarkus.mongodb.panache.common.MongoEntity; import java.time.OffsetDateTime; import java.util.UUID; import org.bson.codecs.pojo.annotations.BsonId;
@MongoEntity(collection="notifications") public class Notification{@BsonId public UUID id;public UUID bookingId;public UUID userId;public String channel;public String type;public String status;public String recipient;public String payloadJson;public String failureReason;public OffsetDateTime createdAt;public OffsetDateTime updatedAt;}
