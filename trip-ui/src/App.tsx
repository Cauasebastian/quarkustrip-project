import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/LoginPage";
import { HomePage } from "./pages/HomePage";
import { FlightsPage } from "./pages/FlightsPage";
import { HotelsPage } from "./pages/HotelsPage";
import { TransportsPage } from "./pages/TransportsPage";
import { NewBookingPage } from "./pages/NewBookingPage";
import { BookingDetailsPage } from "./pages/BookingDetailsPage";
import { ProfilePage } from "./pages/ProfilePage";
import { AdminPage } from "./pages/AdminPage";

export function App() {
  const { authenticated, isAdmin } = useAuth();
  if (!authenticated) return <LoginPage />;

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/catalog/flights" element={<FlightsPage />} />
        <Route path="/catalog/hotels" element={<HotelsPage />} />
        <Route path="/catalog/transports" element={<TransportsPage />} />
        <Route path="/bookings/new" element={<NewBookingPage />} />
        <Route path="/bookings/:id" element={<BookingDetailsPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/admin" element={isAdmin ? <AdminPage /> : <Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
