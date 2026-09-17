import { useAuth } from '../context/AuthContext';

/**
 * Placeholder de la pantalla protegida post-login (T007). El lobby real
 * (crear/unirse a sala, configurar preguntas por jugador) llega en la Fase 2
 * (T012).
 */
export default function LobbyPage() {
  const { user, logout } = useAuth();

  return (
    <div className="lobby-page">
      <h1>¡Hola, {user?.username}!</h1>
      <p>Aquí irá el lobby para crear o unirte a una sala (Fase 2, T012).</p>
      <button type="button" onClick={logout}>
        Cerrar sesión
      </button>
    </div>
  );
}
