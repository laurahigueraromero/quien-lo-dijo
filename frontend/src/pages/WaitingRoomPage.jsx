import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/httpClient';
import { getRoom, startRoom } from '../api/roomApi';
import { useAuth } from '../context/AuthContext';
import { useRoomSocket } from '../hooks/useRoomSocket';
import { MIN_PLAYERS_TO_START } from '../game/constants';
import './WaitingRoomPage.css';

/**
 * Sala de espera: snapshot inicial por REST + eventos en vivo por WebSocket
 * (plan.md §8, T012). Solo el anfitrión ve el botón de iniciar, y solo puede
 * pulsarlo con el mínimo de jugadores alcanzado (spec.md US-1/US-2).
 */
export default function WaitingRoomPage() {
  const { code } = useParams();
  const { user, token } = useAuth();
  const navigate = useNavigate();

  const [room, setRoom] = useState(null);
  const [error, setError] = useState(null);
  const [starting, setStarting] = useState(false);
  const { event } = useRoomSocket(code, token);

  useEffect(() => {
    let cancelled = false;
    getRoom(code)
      .then((state) => {
        if (!cancelled) setRoom(state);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : 'No se pudo cargar la sala');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [code]);

  useEffect(() => {
    if (!event) return;
    if (event.type === 'ROOM_STATE') {
      setRoom(event.payload);
    } else if (event.type === 'ROOM_CLOSED') {
      navigate('/', { replace: true });
    }
  }, [event, navigate]);

  async function handleStart() {
    setError(null);
    setStarting(true);
    try {
      const state = await startRoom(code);
      setRoom(state);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar la partida');
    } finally {
      setStarting(false);
    }
  }

  if (error && !room) {
    return (
      <div className="waiting-room-page">
        <p className="waiting-room-error">{error}</p>
      </div>
    );
  }

  if (!room) {
    return <div className="waiting-room-page">Cargando sala…</div>;
  }

  const isHost = room.hostUserId === user?.userId;
  const canStart = room.players.length >= MIN_PLAYERS_TO_START;

  return (
    <div className="waiting-room-page">
      <div className="waiting-room-card">
        <div className="waiting-room-code">
          <span>Código de sala</span>
          <span className="code">{room.code}</span>
        </div>

        {error && <p className="waiting-room-error">{error}</p>}

        {room.status === 'WAITING' ? (
          <>
            <ul className="waiting-room-players">
              {room.players.map((player) => (
                <li key={player.userId}>
                  <span>{player.username}</span>
                  {player.userId === room.hostUserId && <span className="host-tag">Anfitrión</span>}
                </li>
              ))}
            </ul>

            {isHost ? (
              <>
                <button type="button" onClick={handleStart} disabled={!canStart || starting}>
                  {starting ? 'Iniciando…' : 'Iniciar partida'}
                </button>
                {!canStart && (
                  <p className="waiting-room-hint">
                    Se necesitan al menos {MIN_PLAYERS_TO_START} jugadores para empezar.
                  </p>
                )}
              </>
            ) : (
              <p className="waiting-room-hint">Esperando a que el anfitrión inicie la partida…</p>
            )}
          </>
        ) : (
          <p className="waiting-room-hint">
            ¡Partida iniciada! La fase de preguntas se implementa en la siguiente fase (Fase 3).
          </p>
        )}
      </div>
    </div>
  );
}
