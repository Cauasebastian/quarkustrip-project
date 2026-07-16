package org.sebastiandev.trip.notification.domain; import io.quarkus.mongodb.panache.common.MongoEntity; import org.bson.codecs.pojo.annotations.BsonId;
@MongoEntity(collection="templates") public class NotificationTemplate{@BsonId public String id;public String subject;public String body;public boolean active;}
