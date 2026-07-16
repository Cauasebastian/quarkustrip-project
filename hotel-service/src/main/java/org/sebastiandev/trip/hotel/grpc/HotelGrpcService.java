package org.sebastiandev.trip.hotel.grpc;

import io.grpc.Status; import io.quarkus.grpc.GrpcService; import io.quarkus.hibernate.reactive.panache.Panache; import io.smallrye.mutiny.Uni; import jakarta.inject.Inject;
import java.time.LocalDate; import java.util.*; import org.sebastiandev.trip.contracts.grpc.*; import org.sebastiandev.trip.hotel.domain.*; import org.sebastiandev.trip.hotel.repository.*;
import org.hibernate.reactive.mutiny.Mutiny;

@GrpcService public class HotelGrpcService implements HotelQueryService {
 @Inject HotelRepository hotels; @Inject RoomRepository rooms; @Inject HotelReservationRepository reservations; @Inject Mutiny.SessionFactory sessionFactory;
 @Override public Uni<SearchHotelsResponse> searchHotels(SearchHotelsRequest request){return hotels.find("lower(city) = ?1 and upper(country) = ?2",request.getCity().toLowerCase(),request.getCountry().toUpperCase()).list().map(values->SearchHotelsResponse.newBuilder().addAllHotels(values.stream().map(this::hotelView).toList()).build());}
 @Override public Uni<GetRoomResponse> getRoom(GetRoomRequest request){try{UUID id=UUID.fromString(request.getRoomId());LocalDate in=date(request.getCheckIn()),out=date(request.getCheckOut());return rooms.findById(id).onItem().ifNull().failWith(Status.NOT_FOUND.asRuntimeException()).chain(room->reservations.count("roomId = ?1 and status in (?2, ?3) and checkIn < ?4 and checkOut > ?5",id,HotelReservation.Status.HELD,HotelReservation.Status.CONFIRMED,out,in).map(count->GetRoomResponse.newBuilder().setRoom(roomView(room,count==0)).build()));}catch(RuntimeException e){return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());}}
 @Override public Uni<ListRoomsResponse> listRooms(ListRoomsRequest request){
  try{
   UUID hotelId=UUID.fromString(request.getHotelId());LocalDate in=date(request.getCheckIn()),out=date(request.getCheckOut());
   if(!out.isAfter(in))return Uni.createFrom().failure(Status.INVALID_ARGUMENT.withDescription("checkOut must be after checkIn").asRuntimeException());
   String query="select r.*, not exists (select 1 from hotel_reservations hr where hr.room_id = r.id and hr.status in ('HELD','CONFIRMED') and hr.check_in < ?2 and hr.check_out > ?1) as available from rooms r where r.hotel_id = ?3 and r.active = true order by r.room_number";
   return sessionFactory.withSession(session->session.createNativeQuery(query,Object[].class).setParameter(1,in).setParameter(2,out).setParameter(3,hotelId).getResultList())
    .map(rowsResult->{ListRoomsResponse.Builder response=ListRoomsResponse.newBuilder();for(Object[] row:rowsResult){Room room=new Room();room.id=(UUID)row[0];room.hotel=new Hotel();room.hotel.id=(UUID)row[1];room.roomNumber=(String)row[2];room.roomType=(String)row[3];room.nightlyPriceMinor=((Number)row[4]).longValue();room.currency=(String)row[5];room.active=(Boolean)row[6];response.addRooms(roomView(room,(Boolean)row[8]));}return response.build();});
  }catch(RuntimeException e){return Uni.createFrom().failure(Status.INVALID_ARGUMENT.withDescription("invalid hotel or stay interval").asRuntimeException());}
 }
 @Override public Uni<CreateHotelResponse> createHotel(CreateHotelRequest request){Hotel hotel=new Hotel();hotel.id=UUID.randomUUID();hotel.name=request.getName();hotel.address=request.getAddress();hotel.city=request.getCity();hotel.country=request.getCountry().toUpperCase();hotel.rating=request.getRating();return Panache.withTransaction(()->hotels.persist(hotel)).map(value->CreateHotelResponse.newBuilder().setHotel(hotelView(value)).build());}
 @Override public Uni<CreateRoomResponse> createRoom(CreateRoomRequest request){try{UUID hotelId=UUID.fromString(request.getHotelId());return Panache.withTransaction(()->hotels.findById(hotelId).onItem().ifNull().failWith(Status.NOT_FOUND.asRuntimeException()).chain(hotel->{Room room=new Room();room.id=UUID.randomUUID();room.hotel=hotel;room.roomNumber=request.getRoomNumber();room.roomType=request.getRoomType();room.nightlyPriceMinor=request.getNightlyPrice().getAmountMinor();room.currency=request.getNightlyPrice().getCurrency();room.active=true;return rooms.persist(room);})).map(room->CreateRoomResponse.newBuilder().setRoom(roomView(room,true)).build());}catch(IllegalArgumentException e){return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());}}
 private HotelView hotelView(Hotel h){return HotelView.newBuilder().setId(h.id.toString()).setName(h.name).setAddress(h.address).setCity(h.city).setCountry(h.country).setRating(h.rating).build();}
 private RoomView roomView(Room r,boolean available){return RoomView.newBuilder().setId(r.id.toString()).setHotelId(r.hotel.id.toString()).setRoomNumber(r.roomNumber).setRoomType(r.roomType).setNightlyPrice(Money.newBuilder().setCurrency(r.currency).setAmountMinor(r.nightlyPriceMinor)).setAvailable(available).build();}
 private LocalDate date(LocalDateValue d){return LocalDate.of(d.getYear(),d.getMonth(),d.getDay());}
}
