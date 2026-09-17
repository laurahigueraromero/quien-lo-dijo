import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/httpClient';
import { getRoom, submitQuestions } from '../api/roomApi';
import { useAuth } from '../context/AuthContext';
import { useRoomSocket } from '../hooks/useRoomSocket';
import './QuestionSubmissionPage.css';

/**
 * Cada jugador aporta sus preguntas antes de que arranque la partida
 * (spec.md US-3, plan.md §4, T015). La sala pasa sola a IN_PROGRESS en
 * cuanto el último jugador pendiente envía las suyas (T014); el motor de
 * rondas que arranca a partir de ahí es la Fase 4 (T016).
 */
export default function QuestionSubmissionPage() {
  const { code } = useParams();
  const { token } = useAuth();
  const navigate = useNavigate();

  const [room, setRoom] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const { event } = useRoomSocket(code, token);

  useEffect(() => {
    let cancelled = false;
    getRoom(code)
      .then((state) => {
        if (cancelled) return;
        setRoom(state);
        setQuestions(Array(state.questionsPerPlayer).fill(''));
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

  async function handleSubmit(formEvent) {
    formEvent.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const state = await submitQuestions(
        code,
        questions.map((q) => q.trim()),
      );
      setRoom(state);
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudieron enviar las preguntas');
    } finally {
      setSubmitting(false);
    }
  }

  function updateQuestion(index, value) {
    setQuestions((prev) => prev.map((q, i) => (i === index ? value : q)));
  }

  if (!room) {
    return <div className="questions-page">Cargando…</div>;
  }

  if (room.status === 'IN_PROGRESS') {
    return (
      <div className="questions-page">
        <p className="questions-hint">
          ¡Todos han enviado sus preguntas! El motor de rondas se implementa en la Fase 4.
        </p>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="questions-page">
        <p className="questions-hint">
          Preguntas enviadas. Esperando a que el resto de jugadores ({room.players.length} en total)
          envíen las suyas…
        </p>
      </div>
    );
  }

  return (
    <div className="questions-page">
      <form className="questions-form" onSubmit={handleSubmit}>
        <h1>Escribe tus preguntas</h1>
        {error && <p className="questions-error">{error}</p>}
        {questions.map((question, index) => (
          <label key={index}>
            Pregunta {index + 1}
            <textarea
              value={question}
              onChange={(changeEvent) => updateQuestion(index, changeEvent.target.value)}
              required
              maxLength={280}
            />
          </label>
        ))}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Enviando…' : 'Enviar preguntas'}
        </button>
      </form>
    </div>
  );
}
