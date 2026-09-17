import { Client } from '@stomp/stompjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';

/**
 * Suscripción en vivo a /topic/rooms/{code} (plan.md §5, T011-T012).
 * Devuelve el último RoomEvent recibido ({ type, payload }); el snapshot
 * inicial de la sala se pide aparte por REST (GET /api/rooms/{code}) antes
 * de montar este hook, tal como describe plan.md §8.
 *
 * Nota: es un hook autocontenido, no el "RoomSocketContext" global que
 * esbozaba plan.md §8 — para el alcance actual (Lobby + sala de espera) es
 * suficiente; si las fases de ronda necesitan compartir la conexión entre
 * varios componentes de la misma pantalla, se puede promover a Context sin
 * cambiar su interfaz.
 */
export function useRoomSocket(code, token) {
  const [event, setEvent] = useState(null);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!code || !token) {
      return undefined;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
    });

    client.onConnect = () => {
      setConnected(true);
      client.subscribe(`/topic/rooms/${code}`, (message) => {
        setEvent(JSON.parse(message.body));
      });
      // Errores de las acciones que este jugador dispara (ej. respuesta duplicada, T017);
      // solo le llegan a él, vía la cola de usuario que rellena @SendToUser en el backend.
      client.subscribe('/user/queue/errors', (message) => {
        setEvent({ type: 'ERROR', payload: JSON.parse(message.body) });
      });
    };
    client.onWebSocketClose = () => setConnected(false);

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [code, token]);

  // Envía una acción de cliente a servidor a /app/rooms/{code}/{action} (ej. "answer", T017/T020).
  const sendMessage = useCallback(
    (action, body) => {
      clientRef.current?.publish({
        destination: `/app/rooms/${code}/${action}`,
        body: JSON.stringify(body),
      });
    },
    [code],
  );

  return { event, connected, sendMessage };
}
