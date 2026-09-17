import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createRoom, joinRoom } from '../api/roomApi';
import { ApiError } from '../api/httpClient';
import { useAuth } from '../context/AuthContext';
import './LobbyPage.css';

/**
 * Lobby: crear una sala nueva (configurando questionsPerPlayer) o unirse a
 * una existente por código (spec.md US-1/US-2, plan.md §8, T012).
 */
export default function LobbyPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [questionsPerPlayer, setQuestionsPerPlayer] = useState(1);
  const [joinCode, setJoinCode] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleCreate(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const room = await createRoom(Number(questionsPerPlayer));
      navigate(`/rooms/${room.code}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo crear la sala');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleJoin(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const code = joinCode.trim().toUpperCase();
      await joinRoom(code);
      navigate(`/rooms/${code}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo unir a la sala');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="lobby-page">
      <div className="lobby-header">
        <h1>¡Hola, {user?.username}!</h1>
        <button type="button" onClick={logout}>
          Cerrar sesión
        </button>
      </div>

      {error && <p className="lobby-error">{error}</p>}

      <div className="lobby-cards">
        <form className="lobby-card" onSubmit={handleCreate}>
          <h2>Crear sala</h2>
          <label>
            Preguntas por jugador
            <input
              type="number"
              min={1}
              max={20}
              value={questionsPerPlayer}
              onChange={(event) => setQuestionsPerPlayer(event.target.value)}
              required
            />
          </label>
          <button type="submit" disabled={submitting}>
            Crear
          </button>
        </form>

        <form className="lobby-card" onSubmit={handleJoin}>
          <h2>Unirse a una sala</h2>
          <label>
            Código de sala
            <input
              value={joinCode}
              onChange={(event) => setJoinCode(event.target.value)}
              required
              maxLength={6}
              placeholder="ABC123"
              style={{ textTransform: 'uppercase' }}
            />
          </label>
          <button type="submit" disabled={submitting}>
            Unirme
          </button>
        </form>
      </div>
    </div>
  );
}
