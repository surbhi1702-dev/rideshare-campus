import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import { GuestOnly, RequireAdmin, RequireAuth } from './components/Guards.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Dashboard from './pages/Dashboard.jsx';
import RideForm from './pages/RideForm.jsx';
import SearchRides from './pages/SearchRides.jsx';
import RideDetails from './pages/RideDetails.jsx';
import Matches from './pages/Matches.jsx';
import MyRides from './pages/MyRides.jsx';
import Notifications from './pages/Notifications.jsx';
import Profile from './pages/Profile.jsx';
import Admin from './pages/Admin.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<Dashboard />} />
        <Route path="rides/new" element={<RideForm />} />
        <Route path="rides/:id" element={<RideDetails />} />
        <Route path="rides/:id/edit" element={<RideForm />} />
        <Route path="rides/:id/matches" element={<Matches />} />
        <Route path="search" element={<SearchRides />} />
        <Route path="my-rides" element={<MyRides />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="profile" element={<Profile />} />
        <Route path="admin" element={<RequireAdmin><Admin /></RequireAdmin>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
