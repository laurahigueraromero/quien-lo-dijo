import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getRoom } from '../api/roomApi';
import { useRoomSocket } from '../hooks/useRoomSocket';
import { useAuth } from '../context/AuthContext';
import './RoundAnsweringPage.css';

/**
 * Fase "Responder" de una ronda (spec.md US-4, plan.md §8, T020). El estado de
 * la ronda llega solo por WebSocket (no hay snapshot REST de la ronda activa
 * todavía); por eso se arranca con el evento que trajo la navegación desde
 * QuestionSubmissionPage (location.state) y se sigue escuchando por si acaso.
 */
export default function RoundAnsweringPage() {
  const { code } = useParams();
  const { user, token } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { event, sendMessage } = useRoomSocket(code, token);

  const [room, setRoom] = useState(null);
  const [round, setRound] = useState(location.state ?? null);
  const [answerText, setAnswerText] = useState('');
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
        // Solo se usa para saber si estoy eliminado (vista de espectador); sin ella se asume que no.
      });
    return () => {
      cancelled = true;
    };
  }, [code]);

  useEffect(() => {
    if (!event) return;
    if (event.type === 'ROOM_STATE') {
      setRoom(event.payload);
    } else if (event.type === 'ROUND_ANSWERING_STARTED') {
      setRound(event.payload);
      setAnswerText('');
      setSubmitted(false);
      setError(null);
    } else if (event.type === 'ROUND_BETTING_STARTED') {
      // Todos han respondido (o se acabó el tiempo): arranca la fase de apuestas (Fase 5, T025).
      navigate(`/rooms/${code}/bet`, { replace: true, state: event.payload });
    } else if (event.type === 'ERROR') {
      // Ej. "Ya has respondido a esta ronda": deshacemos el "enviado" para que
      // el jugador vea que no se guardó y pueda intentarlo de nuevo (T017).
      setError(event.payload.message);
      setSubmitted(false);
    } else if (event.type === 'ROOM_CLOSED') {
      navigate('/', { replace: true });
    }
  }, [event, navigate, code]);

  useEffect(() => {
    if (!round) return undefined;
    const endsAt = new Date(round.answeringEndsAt).getTime();
    const tick = () => setRemainingMs(Math.max(0, endsAt - Date.now()));
    tick();
    const id = setInterval(tick, 250);
    return () => clearInterval(id);
  }, [round]);

  function handleSubmit(formEvent) {
    formEvent.preventDefault();
    setError(null);
    sendMessage('answer', { text: answerText.trim() });
    setSubmitted(true);
  }

  if (!round) {
    return <div className="round-page">Esperando la primera pregunta…</div>;
  }

  const seconds = remainingMs != null ? Math.ceil(remainingMs / 1000) : null;
  const amEliminated = room?.players.find((p) => p.userId === user?.userId)?.eliminated ?? false;

  return (
    <div className="round-page">
      <div className="round-card">
        {seconds != null && <p className="round-timer">{seconds}s</p>}
        <h1>{round.questionText}</h1>
        {error && <p className="round-error">{error}</p>}
        {amEliminated ? (
          <p className="round-hint">Estás eliminado/a: sigues viendo la partida, pero ya no puedes responder.</p>
        ) : submitted ? (
          <p className="round-hint">Respuesta enviada. Esperando al resto de jugadores…</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <textarea
              value={answerText}
              onChange={(changeEvent) => setAnswerText(changeEvent.target.value)}
              required
              maxLength={280}
              autoFocus
            />
            <button type="submit">Enviar respuesta</button>
          </form>
        )}
      </div>
    </div>
  );
}
