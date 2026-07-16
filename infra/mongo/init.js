db = db.getSiblingDB('notifications');
db.createCollection('notifications');
db.createCollection('templates');
db.createCollection('processed_events');
db.createCollection('user_contacts');
db.notifications.createIndex({ bookingId: 1, type: 1 });
db.templates.insertOne({ _id: 'booking-terminal-v1', subject: 'Trip booking status', body: 'Your booking status changed.', active: true });
