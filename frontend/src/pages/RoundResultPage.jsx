import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getRoom } from '../api/roomApi';
import { useAuth } from '../context/AuthContext';
import { useRoomSocket } from '../hooks/useRoomSocket';
import './RoundResultPage.css';

/**
 * Resultado de una ronda ya resuelta (spec.md US-6/US-7, plan.md §8, T030): revela al
 * autor, marca cada apuesta como acierto/fallo, muestra el marcador actualizado y avisa
 * de nuevas eliminaciones. La siguiente ronda (si la hay) llega sola por WebSocket.
 */
export default function RoundResultPage() {
  const { code } = useParams();
  const { token } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { event } = useRoomSocket(code, token);

  const [room, setRoom] = useState(null);
  const [result, setResult] = useState(location.state ?? null);

  useEffect(() => {
    let cancelled = false;
    getRoom(code)
      .then((state) => {
        if (!cancelled) setRoom(state);
      })
      .catch(() => {
        // Solo se usa para mostrar nombres de usuario; sin ella se muestran los ids.
      });
    return () => {
      cancelled = true;
    };
  }, [code]);

  useEffect(() => {
    if (!event) return;
    if (event.type === 'ROOM_STATE') {
      setRoom(event.payload);
    } else if (event.type === 'ROUND_RESOLVED') {
      setResult(event.payload);
    } else if (event.type === 'ROUND_ANSWERING_STARTED') {
      // Ha arrancado la siguiente ronda (Fase 4/6).
      navigate(`/rooms/${code}/play`, { replace: true, state: event.payload });
    } else if (event.type === 'ROOM_CLOSED') {
      navigate('/', { replace: true });
    }
  }, [event, navigate, code]);

  if (!result || !room) {
    return <div className="result-page">Cargando resultado…</div>;
  }

  const usernameOf = (userId) => room.players.find((p) => p.userId === userId)?.username ?? `Jugador ${userId}`;
  const eliminatedSet = new Set(result.newlyEliminatedUserIds);

  return (
    <div className="result-page">
      <div className="result-card">
        <h1>Resultado de la ronda</h1>
        <p className="result-author">
          La respuesta era de <strong>{usernameOf(result.authorUserId)}</strong>
        </p>

        {result.newlyEliminatedUserIds.length > 0 && (
          <p className="result-eliminated-banner">
            {result.newlyEliminatedUserIds.map(usernameOf).join(', ')} se ha quedado sin fichas y queda eliminado/a.
          </p>
        )}

        <ul className="result-bets">
          {result.bets.map((bet, index) => (
            <li key={index}>
              <span>
                {usernameOf(bet.bettorUserId)} → {usernameOf(bet.candidateUserId)} ({bet.amount} fichas)
                {bet.autoAssigned && <span className="auto-tag"> · automática</span>}
              </span>
              <span className={bet.correct ? 'correct' : 'incorrect'}>{bet.correct ? '✓ acierto' : '✗ fallo'}</span>
            </li>
          ))}
        </ul>

        <ul className="result-balances">
          {result.balances.map((balance) => (
            <li key={balance.userId} className={balance.eliminated ? 'eliminated' : ''}>
              <span>
                {usernameOf(balance.userId)}
                {eliminatedSet.has(balance.userId) && ' 💀'}
              </span>
              <span>{balance.saldoFichas} fichas</span>
            </li>
          ))}
        </ul>

        <p className="result-hint">Esperando la siguiente ronda…</p>
      </div>
    </div>
  );
}
