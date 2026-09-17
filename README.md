# ¿Quién lo dijo?

Juego de fiesta multijugador (3+ jugadores): se lanza una pregunta, todos responden en texto libre, sale una respuesta al azar sin revelar su autor y el resto apuesta fichas a quién creen que la escribió. Se reparten fichas según aciertos y fallos; gana quien más fichas tenga al final de la partida.

Desarrollado siguiendo la metodología **Spec-Driven Development (SDD)**: primero la especificación funcional, luego el plan técnico, luego el desglose en tareas, y por último la implementación.

## Documentación (SDD)

Toda la especificación de la primera feature vive en [`specs/001-adivina-quien-respondio/`](specs/001-adivina-quien-respondio):

- [`spec.md`](specs/001-adivina-quien-respondio/spec.md) — Qué hace el juego: historias de usuario, reglas de negocio (economía de fichas), casos límite y fuera de alcance.
- [`plan.md`](specs/001-adivina-quien-respondio/plan.md) — Cómo se construye: arquitectura (Spring Boot + React + WebSocket STOMP), modelo de datos, máquina de estados, contratos de API/WebSocket.
- [`tasks.md`](specs/001-adivina-quien-respondio/tasks.md) — Desglose en tareas implementables, trazadas a cada historia de usuario (estado de cada fase marcado ahí).

## Stack

- **Backend:** Spring Boot (Web, Security, WebSocket, Data JPA)
- **Frontend:** React (Vite) + `@stomp/stompjs` sobre SockJS
- **Base de datos:** H2 en desarrollo local, PostgreSQL en Docker/producción

## Cómo levantarlo

### Opción A: con Docker (recomendado para probarlo, incluida la pantalla del móvil)

Requiere Docker Desktop. Levanta Postgres + backend + frontend (servido por nginx, que hace de reverse proxy hacia el backend, así que todo se sirve por un único puerto):

```bash
docker compose up --build
```

- Desde el propio PC: **http://localhost:8081**
- Desde el móvil (en la misma red WiFi que el PC): **http://<IP-LAN-del-PC>:8081**

Para averiguar la IP LAN del PC:
- Windows: `ipconfig` (busca "Dirección IPv4" del adaptador WiFi)
- macOS/Linux: `ifconfig` o `ip addr` (busca la IP de tipo `192.168.x.x`)

Para parar todo: `docker compose down` (añade `-v` si además quieres borrar los datos de Postgres).

> Nota: los orígenes permitidos de CORS/WebSocket están en modo comodín (`*`) a propósito, porque la IP LAN no se conoce de antemano — es una simplificación aceptable para desarrollo/red local, no para un despliegue expuesto a internet real (ver comentarios en `WebConfig`/`WebSocketConfig`).

### Opción B: en local, sin Docker (para desarrollar)

Backend (perfil `dev`, base de datos H2 en memoria):

```bash
cd backend
./gradlew bootRun
```

Frontend (proxy de Vite hacia `localhost:8080`):

```bash
cd frontend
npm install
npm run dev
```

Abre **http://localhost:5173**.

## Estado del proyecto

Fases 0 a 6 completadas (setup, autenticación, salas, preguntas, y el motor de rondas completo: responder, apostar, resolución económica y eliminación). Ver `tasks.md` para el detalle de cada fase y lo que queda (Fase 7: fin de partida, historial y ranking).
