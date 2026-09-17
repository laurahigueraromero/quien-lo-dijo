import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getRoom } from '../api/roomApi';
import { useAuth } from '../context/AuthContext';
import { useRoomSocket } from '../hooks/useRoomSocket';
import './RoundBettingPage.css';

/**
 * Fase "Apostar" de una ronda (spec.md US-5, plan.md §8, T025). El backend nunca revela
 * quién es el autor: si mi userId NO aparece en candidateUserIds, soy yo (spec.md US-5) y
 * veo una pantalla de espera en vez del formulario de apuesta.
 */
export default function RoundBettingPage() {
  const { code } = useParams();
  const { user, token } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { event, sendMessage } = useRoomSocket(code, token);

  const [room, setRoom] = useState(null);
  const [betting, setBetting] = useState(location.state ?? null);
  const [candidateUserId, setCandidateUserId] = useState('');
  const [amount, setAmount] = useState(1);
  const [submitted, setSubmitted] = useState(false);
  const [remainingMs, setRemainingMs] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getRoom(code)
      .then((state) => {
        if (!cancelled) setRoom(state);
      })
      .catch(() => {
        // El snapshot es solo para mostrar nombres de usuario y saldo; si falla,
        // el formulario de apuesta simplemente no se puede rellenar todavía.
      });
    return () => {
      cancelled = true;
    };
  }, [code]);

  useEffect(() => {
    if (!event) return;
    if (event.type === 'ROOM_STATE') {
      setRoom(event.payload);
    } else if (event.type === 'ROUND_BETTING_STARTED') {
      setBetting(event.payload);
      setSubmitted(false);
      setError(null);
      setCandidateUserId('');
      setAmount(1);
    } else if (event.type === 'ROUND_RESOLVED') {
      // Se cerró la fase de apuestas (todos apostaron o venció el tiempo): T026/T030.
      navigate(`/rooms/${code}/result`, { replace: true, state: event.payload });
    } else if (event.type === 'ERROR') {
      setError(event.payload.message);
      setSubmitted(false);
    } else if (event.type === 'ROOM_CLOSED') {
      navigate('/', { replace: true });
    }
  }, [event, navigate, code]);

  useEffect(() => {
    if (!betting) return undefined;
    const endsAt = new Date(betting.bettingEndsAt).getTime();
    const tick = () => setRemainingMs(Math.max(0, endsAt - Date.now()));
    tick();
    const id = setInterval(tick, 250);
    return () => clearInterval(id);
  }, [betting]);

  function handleSubmit(formEvent) {
    formEvent.preventDefault();
    setError(null);
    sendMessage('bet', { candidateUserId: Number(candidateUserId), amount: Number(amount) });
    setSubmitted(true);
  }

  if (!betting || !room) {
    return <div className="round-page">Cargando…</div>;
  }

  const myUserId = user?.userId;
  const isAuthor = !betting.candidateUserIds.includes(myUserId);
  const me = room.players.find((p) => p.userId === myUserId);
  const mySaldo = me?.saldoFichas ?? 0;
  const candidates = betting.candidateUserIds.filter((id) => id !== myUserId);
  const usernameOf = (id) => room.players.find((p) => p.userId === id)?.username ?? `Jugador ${id}`;

  const seconds = remainingMs != null ? Math.ceil(remainingMs / 1000) : null;

  return (
    <div className="round-page">
      <div className="round-card">
        {seconds != null && <p className="round-timer">{seconds}s</p>}
        <h1>¿Quién crees que respondió esto?</h1>
        <p className="shown-answer">«{betting.shownAnswerText}»</p>
        {error && <p className="round-error">{error}</p>}

        {isAuthor ? (
          <p className="round-hint">Esta es tu respuesta. Espera a ver si consigues despistar al resto…</p>
        ) : me?.eliminated ? (
          <p className="round-hint">Estás eliminado/a: sigues viendo la partida, pero ya no puedes apostar.</p>
        ) : submitted ? (
          <p className="round-hint">Apuesta enviada. Esperando al resto de jugadores…</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <label>
              Candidato
              <select
                value={candidateUserId}
                onChange={(changeEvent) => setCandidateUserId(changeEvent.target.value)}
                required
              >
                <option value="" disabled>
                  Elige un jugador
                </option>
                {candidates.map((id) => (
                  <option key={id} value={id}>
                    {usernameOf(id)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Fichas a apostar (máx. {mySaldo})
              <input
                type="number"
                min={1}
                max={mySaldo}
                value={amount}
                onChange={(changeEvent) => setAmount(changeEvent.target.value)}
                required
              />
            </label>
            <button type="submit">Apostar</button>
          </form>
        )}
      </div>
    </div>
  );
}
