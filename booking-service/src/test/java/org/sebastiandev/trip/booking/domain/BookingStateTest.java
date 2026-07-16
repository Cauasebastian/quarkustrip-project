package org.sebastiandev.trip.booking.domain;
import static org.junit.jupiter.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class BookingStateTest{
 @Test void allItemsRequiresAtLeastOneItem(){Booking booking=new Booking();assertFalse(booking.allItems(BookingItemStatus.HELD));BookingItem first=new BookingItem();first.status=BookingItemStatus.HELD;BookingItem second=new BookingItem();second.status=BookingItemStatus.PENDING;booking.items=List.of(first,second);assertFalse(booking.allItems(BookingItemStatus.HELD));second.status=BookingItemStatus.HELD;assertTrue(booking.allItems(BookingItemStatus.HELD));}
 @Test void detectsItemFailure(){Booking booking=new Booking();BookingItem item=new BookingItem();item.status=BookingItemStatus.FAILED;booking.items=List.of(item);assertTrue(booking.hasItemFailure());}
}
