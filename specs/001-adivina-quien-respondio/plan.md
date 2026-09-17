# Plan Técnico: "¿Quién lo dijo?"

**ID de feature:** 001-adivina-quien-respondio
**Basado en:** `spec.md` (v0.1, sin clarificaciones pendientes)
**Estado:** Borrador v0.1

Este documento traduce la spec funcional a decisiones de diseño técnico: modelo de datos, máquina de estados, contratos de API/WebSocket y estructura de frontend. No contiene código de implementación, solo el diseño sobre el que se derivará `tasks.md`.

---

## 1. Arquitectura general

```
┌─────────────────┐        HTTPS (REST, login/registro/config)        ┌──────────────────────┐
│                  │ ───────────────────────────────────────────────► │                       │
│  React (SPA)     │                                                   │   Spring Boot API     │
│  - Vite + React  │        WebSocket (STOMP sobre SockJS)             │   - REST controllers  │
│    Router        │ ◄────────────────────────────────────────────►  │   - WS controllers     │
│  - @stomp/stompjs│        /topic/rooms/{code}  (broadcast)           │   - Game Engine        │
│  - Context API   │        /app/rooms/{code}/...  (acciones cliente)  │   - Spring Security+JWT│
└─────────────────┘                                                   │   - Spring Data JPA    │
                                                                        └───────────┬───────────┘
                                                                                    │
                                                                                    ▼
                                                                        ┌──────────────────────┐
                                                                        │   Base de datos        │
                                                                        │   (PostgreSQL/H2 dev)  │
                                                                        └──────────────────────┘
```

- **REST** se usa para todo lo que no es tiempo real dentro de una ronda: registro/login, crear/unirse a sala, configurar partida, enviar preguntas, consultar historial y ranking.
- **WebSocket (STOMP/SockJS)** se usa exclusivamente para sincronizar el estado en vivo de una partida ya iniciada: fases de ronda, temporizadores, resultados.
- **MVP de una sola instancia:** el estado en vivo de las partidas (timers activos, fase actual) se mantiene en memoria del proceso Spring Boot (un `GameEngineService` con un mapa `roomCode -> RoomRuntimeState`). No se contempla clúster/escalado horizontal en este MVP (ver sección 8, limitaciones).

## 2. Modelo de datos (entidades JPA)

### `User`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID/Long | PK |
| username | String | único |
| email | String | único |
| passwordHash | String | BCrypt |
| createdAt | Instant | |

### `Room` (partida)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID/Long | PK |
| code | String(6) | único, código de unión, generado al crear |
| hostUserId | FK → User | anfitrión original (solo relevante antes de iniciar) |
| status | Enum | `WAITING`, `COLLECTING_QUESTIONS`, `IN_PROGRESS`, `FINISHED`, `CANCELLED` |
| questionsPerPlayer | int | configurado por el anfitrión antes de iniciar |
| createdAt / startedAt / finishedAt | Instant | |

### `RoomPlayer`
| Campo | Tipo | Notas |
|---|---|---|
| id | PK | |
| roomId | FK → Room | |
| userId | FK → User | |
| saldoFichas | int | inicia en 50 |
| eliminated | boolean | true si saldoFichas llegó a 0 |
| joinedAt | Instant | |

Restricción: único `(roomId, userId)`.

### `Question`
| Campo | Tipo | Notas |
|---|---|---|
| id | PK | |
| roomId | FK → Room | |
| authorPlayerId | FK → RoomPlayer | |
| text | String | |
| discarded | boolean | true si el autor quedó eliminado antes de su turno (regla spec §6) |
| playOrder | int | orden aleatorio asignado al iniciar la fase de rondas |

### `Round`
| Campo | Tipo | Notas |
|---|---|---|
| id | PK | |
| roomId | FK → Room | |
| questionId | FK → Question | |
| status | Enum | `ANSWERING`, `BETTING`, `RESOLVED` |
| selectedAnswerId | FK → Answer (nullable hasta que se sortea) | |
| authorPlayerId | FK → RoomPlayer | autor real de `selectedAnswerId` (denormalizado para consulta rápida) |
| answeringEndsAt / bettingEndsAt | Instant | control de temporizadores |
| resolvedAt | Instant | |

### `Answer`
| Campo | Tipo | Notas |
|---|---|---|
| id | PK | |
| roundId | FK → Round | |
| playerId | FK → RoomPlayer | |
| text | String, nullable | null/"sin respuesta" si no respondió a tiempo |
| submittedAt | Instant | |

Restricción: único `(roundId, playerId)`.

### `Bet`
| Campo | Tipo | Notas |
|---|---|---|
| id | PK | |
| roundId | FK → Round | |
| bettorPlayerId | FK → RoomPlayer | |
| candidatePlayerId | FK → RoomPlayer | a quién apostó |
| amount | int | ≥ 1 |
| correct | boolean, nullable | se rellena al resolver |
| autoAssigned | boolean | true si fue asignada automáticamente por timeout (spec US-5) |

Restricción: único `(roundId, bettorPlayerId)`.

### `GameResult` (historial, uno por jugador y partida)
| Campo | Tipo | Notas |
|---|---|---|
| id | PK | |
| roomId | FK → Room | |
| userId | FK → User | |
| finalFichas | int | |
| aciertos / fallos | int | |
| isWinner | boolean | true si quedó entre los ganadores compartidos |
| playedAt | Instant | |

**Ranking global:** no es una tabla propia; es una consulta agregada `SUM(finalFichas) GROUP BY userId ORDER BY DESC` sobre `GameResult` (criterio fijado en spec §7: fichas acumuladas históricas).

## 3. Máquina de estados

### Partida (`Room.status`)
```
WAITING ──(host: start, mín. 3 jugadores)──► COLLECTING_QUESTIONS
COLLECTING_QUESTIONS ──(todos envían sus preguntas)──► IN_PROGRESS
IN_PROGRESS ──(se resuelve la última ronda válida)──► FINISHED
IN_PROGRESS ──(anfitrión se desconecta)──► CANCELLED   [spec §7]
WAITING ──(anfitrión cierra la sala)──► CANCELLED
```

### Ronda (`Round.status`), dentro de `IN_PROGRESS`
```
[siguiente pregunta no descartada en playOrder]
        │
        ▼
   ANSWERING  (60s, todos los jugadores activos envían Answer)
        │  (timeout o todos respondieron)
        ▼
  se sortea un Answer no vacío → BETTING (30s, todos menos el autor apuestan)
        │  (timeout: auto-asigna apuesta mínima a candidato aleatorio → spec US-5)
        ▼
   RESOLVED  (se aplica la economía de fichas, spec §6; se emite evento con resultados)
        │
        ▼
¿quedan preguntas no descartadas? → sí: siguiente ronda (ANSWERING)
                                   → no: Room.status = FINISHED
```

**Caso borde — todas las respuestas vacías:** si en `ANSWERING` nadie envía una respuesta con contenido, la ronda se resuelve como "sin ganador ni perdedor" (no hay `selectedAnswerId`), se descarta y se pasa a la siguiente pregunta, sin mover fichas de nadie. *(Regla añadida en el plan para cubrir un hueco no cubierto explícitamente por la spec; a validar si aparece como caso real en pruebas.)*

## 4. API REST

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| POST | `/api/auth/register` | Alta de usuario | pública |
| POST | `/api/auth/login` | Devuelve JWT | pública |
| POST | `/api/rooms` | Crea sala; body `{ questionsPerPlayer }`; devuelve `code` | JWT |
| POST | `/api/rooms/{code}/join` | Une al usuario autenticado a la sala | JWT |
| GET | `/api/rooms/{code}` | Snapshot del estado de la sala (jugadores, status, config) | JWT |
| POST | `/api/rooms/{code}/start` | Solo host; requiere ≥3 jugadores; pasa a `COLLECTING_QUESTIONS` | JWT + host |
| POST | `/api/rooms/{code}/questions` | Envía las N preguntas del jugador (`questionsPerPlayer`) | JWT |
| DELETE | `/api/rooms/{code}` | Host cierra la sala (solo si `WAITING`) | JWT + host |
| GET | `/api/users/me/history` | Historial de partidas del usuario (`GameResult`) | JWT |
| GET | `/api/ranking/global` | Top N por fichas acumuladas históricas | JWT |

Las acciones dentro de una ronda (`answer`, `bet`) **no** son REST — van por WebSocket para poder reaccionar en tiempo real y controlar temporizadores del lado servidor.

## 5. Contrato WebSocket (STOMP)

**Conexión:** `CONNECT /ws` con JWT (header `Authorization` en el `CONNECT` frame o interceptor de handshake).

**Suscripción del cliente:** `/topic/rooms/{code}` — recibe todos los eventos de esa sala.

**Mensajes cliente → servidor** (`/app/rooms/{code}/...`):
| Destino | Payload | Cuándo es válido |
|---|---|---|
| `/app/rooms/{code}/answer` | `{ text }` | fase `ANSWERING` de la ronda actual |
| `/app/rooms/{code}/bet` | `{ candidatePlayerId, amount }` | fase `BETTING`, jugador ≠ autor, no eliminado |

**Eventos servidor → cliente** (broadcast a `/topic/rooms/{code}`):
| Evento | Payload (resumen) | Disparado cuando |
|---|---|---|
| `ROOM_STATE` | jugadores, status, config | al conectarse / cambios de lobby |
| `QUESTIONS_PHASE_STARTED` | `questionsPerPlayer` | tras `start` |
| `ROUND_ANSWERING_STARTED` | `roundId, questionText, answeringEndsAt` | inicio de cada ronda |
| `ROUND_BETTING_STARTED` | `roundId, shownAnswerText, candidatePlayerIds, bettingEndsAt` | tras cerrar respuestas (autor excluido de `candidatePlayerIds` implícitamente al no recibir este evento como accionable) |
| `ROUND_RESOLVED` | `roundId, authorPlayerId, bets: [{playerId, candidatePlayerId, amount, correct}], balancesAfter: {playerId: saldo}, eliminated: [playerId]` | al resolver economía |
| `GAME_FINISHED` | `finalStandings: [{playerId, fichas}], winners: [playerId]` | última ronda resuelta |
| `GAME_CANCELLED` | `reason` | anfitrión desconectado en partida (spec §7) |

**Temporizadores:** gestionados en servidor con `TaskScheduler` (uno programado al iniciar cada fase); si la fase se completa antes por acción de todos los jugadores, se cancela el temporizador pendiente. El cliente solo pinta una cuenta atrás visual a partir de `...EndsAt` (timestamp absoluto, no segundos relativos, para evitar desajustes de reloj).

## 6. Lógica de negocio clave (motor de resolución de ronda)

Pseudocódigo de `GameEngineService.resolveRound(round)`, aplicando spec §6:

```
respuestasValidas = answers de la ronda con text != null
si respuestasValidas está vacía:
    marcar ronda como sin ganador, no mover fichas, pasar a siguiente pregunta
    return

autor = jugador de selectedAnswer
apuestas = bets de la ronda (auto-asignadas incluidas)
aciertan = apuestas donde candidatePlayerId == autor.id
fallan   = apuestas donde candidatePlayerId != autor.id
boteFallos = sum(amount de fallan)

si aciertan.isEmpty():
    autor.saldo += boteFallos          // el autor se lleva todo, sin bonus adicional
si no si fallan.isEmpty():             // hay mezcla de aciertos y fallos
    parte = boteFallos / aciertan.size()  // reparto a partes iguales
    para cada bet en aciertan:
        bet.bettor.saldo += bet.amount + parte   // recupera su apuesta + su parte del bote
    autor.saldo += 2 * fallan.size()   // bonus fijo +2 por cada fallo
si no:                                  // todos aciertan, boteFallos == 0
    para cada bet en aciertan:
        bet.bettor.saldo += bet.amount // solo recupera lo apostado

para cada bet en fallan:
    bet.bettor.saldo -= bet.amount     // ya se ha restado al momento de apostar, o se resta aquí (decisión de implementación)

para cada jugador con saldo <= 0:
    marcar eliminated = true
```

*Nota de implementación:* es más simple **descontar la apuesta al momento de apostar** (así el saldo mostrado en `BETTING` ya refleja el riesgo) y solo **sumar** ganancias al resolver, en vez de restar dos veces. Esto se decide en `tasks.md` al implementar, pero el resultado neto por jugador debe coincidir con el pseudocódigo anterior.

**Redondeo:** `parte = boteFallos / aciertan.size()` puede no ser entero exacto. Regla del plan: **truncar hacia abajo (floor)** y el resto (`boteFallos % aciertan.size()`) queda sin repartir (se pierde del sistema). *(Detalle no cubierto en la spec; se registra aquí como decisión de diseño, no requiere volver a preguntar salvo que el resultado no guste al probarlo.)*

## 7. Seguridad

- **Spring Security + JWT:** login devuelve un access token; se envía en `Authorization: Bearer` para REST y en el handshake STOMP para WebSocket.
- **Autorización por sala:** cada acción REST/WS sobre `/rooms/{code}/...` valida que el usuario autenticado sea `RoomPlayer` de esa sala (y, para `start`/`delete`, que sea además el `hostUserId`).
- Passwords con BCrypt; sin OAuth/social login en el MVP.

## 8. Frontend (React)

**Pantallas principales:**
1. Login / Registro
2. Lobby: crear sala (config `questionsPerPlayer`) o unirse por código
3. Sala de espera (lista de jugadores, botón "Iniciar" solo visible para el host)
4. Envío de preguntas (formulario con N campos según config)
5. Ronda — Responder (pregunta + input de texto + cuenta atrás)
6. Ronda — Apostar (respuesta mostrada + lista de candidatos + slider/input de cantidad + cuenta atrás) — no se muestra al autor, que ve una pantalla de espera
7. Ronda — Resultado (autor revelado, quién acertó/falló, saldos actualizados)
8. Resultados finales (clasificación, ganador/es)
9. Historial personal y Ranking global

**Gestión de estado:** un `RoomSocketContext` (React Context + hook `useRoomSocket`) que envuelve la conexión STOMP y expone el estado actual de la sala/ronda a los componentes, evitando introducir Redux para un MVP de este tamaño.

## 9. Limitaciones conocidas del MVP (heredadas de spec §8 + decisiones técnicas)

- Estado de partida en memoria de un único proceso: si el backend se reinicia, las partidas `IN_PROGRESS` se pierden (no hay recuperación tras caída del servidor). Aceptable para MVP; documentar como deuda técnica.
- Sin lógica de reconexión de jugadores individuales (solo auto-skip por timeout, según spec).
- Sin moderación de contenido de preguntas/respuestas.

## 10. Siguiente paso (SDD)

Con este plan como base, el siguiente artefacto es `tasks.md`: desglose en tareas verticales pequeñas (ej. "Alta de usuario + login JWT", "Crear/unirse a sala", "Motor de rondas: fase ANSWERING", "Motor de rondas: fase BETTING + resolución", "Pantallas React de sala y ronda", "Historial y ranking"), cada una con su criterio de "hecho" trazable a una historia de usuario de `spec.md`.
