import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';
import LobbyPage from './pages/LobbyPage';
import LoginPage from './pages/LoginPage';
import QuestionSubmissionPage from './pages/QuestionSubmissionPage';
import RegisterPage from './pages/RegisterPage';
import RoundAnsweringPage from './pages/RoundAnsweringPage';
import RoundBettingPage from './pages/RoundBettingPage';
import RoundResultPage from './pages/RoundResultPage';
import WaitingRoomPage from './pages/WaitingRoomPage';
import './App.css';

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<LobbyPage />} />
          <Route path="/rooms/:code" element={<WaitingRoomPage />} />
          <Route path="/rooms/:code/questions" element={<QuestionSubmissionPage />} />
          <Route path="/rooms/:code/play" element={<RoundAnsweringPage />} />
          <Route path="/rooms/:code/bet" element={<RoundBettingPage />} />
          <Route path="/rooms/:code/result" element={<RoundResultPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}

export default App;
