package org.sebastiandev.trip.notification.domain; import io.quarkus.mongodb.panache.common.MongoEntity; import java.time.OffsetDateTime; import java.util.UUID; import org.bson.codecs.pojo.annotations.BsonId;
@MongoEntity(collection="processed_events") public class ProcessedEvent{@BsonId public UUID eventId;public String type;public OffsetDateTime processedAt;}
