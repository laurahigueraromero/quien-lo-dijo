# Tareas de Implementación: "¿Quién lo dijo?"

**ID de feature:** 001-adivina-quien-respondio
**Basado en:** `spec.md` (v0.1) + `plan.md` (v0.1)

Convenciones:
- `T0XX` = identificador de tarea, secuencial dentro de su fase.
- `[P]` = paralelizable (toca archivos/módulos distintos de otras tareas `[P]` de la misma fase; se puede repartir entre dos personas o dos sesiones de trabajo sin bloquearse).
- Cada tarea indica **Depende de** y **Criterio de hecho**, y cuando aplica, la **Historia de usuario** de `spec.md` a la que da cobertura (trazabilidad).
- Fases pensadas para completarse en orden; dentro de una fase, hacer primero las no-`[P]` si son prerequisito de las `[P]`.

---

## Fase 0 — Setup del proyecto ✅ Completada

- **T001. ✅** Inicializar proyecto backend Spring Boot (Web, Security, WebSocket, Data JPA, Validation) con Gradle/Maven, perfil `dev` con base de datos H2 en memoria y perfil `prod`-ready con PostgreSQL.
  *Depende de:* nada. *Hecho cuando:* `./gradlew bootRun` (o equivalente) levanta la app y responde en un endpoint de salud (`/actuator/health` o similar).
  *Nota:* generado con Spring Boot 4.1.1 / Java 21 (toolchain con auto-provisioning vía plugin Foojay); endpoint de verificación en `/api/health` (ver T003).

- **T002. [P] ✅** Inicializar proyecto frontend React (Vite) con React Router, cliente `@stomp/stompjs` + `sockjs-client`, y estructura de carpetas (`pages/`, `components/`, `context/`, `api/`).
  *Depende de:* nada. *Hecho cuando:* `npm run dev` sirve una pantalla en blanco navegable con routing básico.

- **T003. ✅** Configurar CORS y proxy dev (frontend → backend) para que REST y WebSocket funcionen en local sin fricción.
  *Depende de:* T001, T002. *Hecho cuando:* el frontend puede hacer una petición REST de prueba al backend sin error de CORS.
  *Nota:* verificado end-to-end (`curl http://localhost:5173/api/health` a través del proxy de Vite devuelve `200 {"status":"ok"}`). Seguridad de Spring configurada en modo `permitAll()` temporal hasta la Fase 1 (JWT).

- **T004. ✅** Crear las entidades JPA descritas en `plan.md` §2 (`User`, `Room`, `RoomPlayer`, `Question`, `Round`, `Answer`, `Bet`, `GameResult`) con sus repositorios Spring Data.
  *Depende de:* T001. *Hecho cuando:* el esquema se genera correctamente (Flyway/Hibernate DDL) y hay un repositorio JPA por entidad.
  *Nota:* verificado arrancando la app: Hibernate generó las 8 tablas con todas las FKs y restricciones únicas sin errores.

---

## Fase 1 — Autenticación ✅ Completada (soporta todas las historias, es prerequisito transversal)

- **T005. ✅** Endpoint `POST /api/auth/register` (alta de `User` con password BCrypt) + `POST /api/auth/login` (devuelve JWT).
  *Depende de:* T004. *Hecho cuando:* un usuario puede registrarse y loguearse, y el login devuelve un token válido.
  *Nota:* verificado con curl end-to-end: 201 en registro, 200 con token en login, 409 en username duplicado, 401 en password incorrecta.

- **T006. ✅** Filtro/interceptor JWT para proteger endpoints REST (`Authorization: Bearer`) y autenticar el handshake WebSocket (`plan.md` §7).
  *Depende de:* T005. *Hecho cuando:* un endpoint protegido rechaza peticiones sin token válido, y una conexión STOMP autenticada puede identificar al `User`.
  *Nota:* `JwtAuthenticationFilter` (REST) y `StompAuthChannelInterceptor` (frame CONNECT) implementados; verificado que una ruta protegida sin token da 403 y con token pasa la autenticación (404 por no existir aún, no 401/403). Encontrado y corregido un caso borde: `/error` debe estar en `permitAll()` o Spring Security pisa con 403 el código real (409/401) de los `ResponseStatusException`.

- **T007. [P] ✅** Pantallas React de Registro y Login + almacenamiento del token (memoria/contexto, no localStorage persistente si se prioriza seguridad) + cliente API con interceptor que añade el header.
  *Depende de:* T002. *Hecho cuando:* un usuario puede registrarse y loguearse desde la UI y navegar a una pantalla protegida (Lobby).
  *Nota:* `AuthContext` + `ProtectedRoute` + `LoginPage`/`RegisterPage` + placeholder `LobbyPage`. Verificado con `npm run build` y `npm run lint` limpios, y proxy `/api` confirmado sirviendo `/api/health` a través de Vite; la comprobación manual en el navegador queda para cuando se abra la app.

---

## Fase 2 — Gestión de salas ✅ Completada (US-1, US-2)

- **T008. ✅** Endpoint `POST /api/rooms` (crea sala en `WAITING`, genera `code` único, guarda `questionsPerPlayer`) y `GET /api/rooms/{code}` (snapshot).
  *Depende de:* T006. *Cubre:* US-1, US-2. *Hecho cuando:* un usuario autenticado crea una sala y recibe su código.
  *Nota:* código de 6 caracteres sin I/O/0/1 (evita confusiones al leerlo/escribirlo); el host se añade automáticamente como `RoomPlayer` al crear.

- **T009. ✅** Endpoint `POST /api/rooms/{code}/join` (añade `RoomPlayer` con saldo inicial 50, valida sala en `WAITING`).
  *Depende de:* T008. *Cubre:* US-1. *Hecho cuando:* varios usuarios distintos pueden unirse a la misma sala por código.
  *Nota:* unirse dos veces es idempotente (no duplica `RoomPlayer`); unirse a una sala ya iniciada da 409.

- **T010. ✅** Endpoint `POST /api/rooms/{code}/start` (solo host, valida mínimo 3 jugadores, pasa a `COLLECTING_QUESTIONS`) y `DELETE /api/rooms/{code}` (host cierra sala en `WAITING`).
  *Depende de:* T009. *Cubre:* US-1, US-2, spec §9 (anfitrión). *Hecho cuando:* solo el host puede iniciar/cerrar, y no se puede iniciar por debajo del mínimo.
  *Nota:* verificado con curl: 400 con &lt;3 jugadores, 403 si no es el host, 200+`COLLECTING_QUESTIONS` con 3 jugadores.

- **T011. [P] ✅** Canal WebSocket base: al conectar a `/topic/rooms/{code}`, el servidor emite `ROOM_STATE` con jugadores actuales y status; se reemite en cada `join`/cambio de config.
  *Depende de:* T006, T009. *Hecho cuando:* dos clientes conectados a la misma sala ven en tiempo real cuando un tercero se une.
  *Nota de diseño:* implementado como snapshot inicial por REST (`GET /api/rooms/{code}`) + broadcast por WS a partir de ahí, en vez de emitir el estado en el propio evento de conexión STOMP (evita el matiz de que `/topic/**` no pasa por los `@SubscribeMapping` de la app al usar el broker prefix por defecto). Verificado con un cliente STOMP real (`@stomp/stompjs` sobre WebSocket nativo): al hacer `join`, el evento `{"type":"ROOM_STATE",...}` llega correctamente a un cliente ya suscrito.

- **T012. [P] ✅** Pantallas React: Lobby (crear sala con `questionsPerPlayer`, unirse por código) y Sala de espera (lista de jugadores en vivo, botón "Iniciar" solo para host).
  *Depende de:* T007, T011. *Cubre:* US-1, US-2. *Hecho cuando:* 3 navegadores/pestañas distintas pueden crear, unirse y ver la lista actualizarse en vivo.
  *Nota:* `useRoomSocket` (hook autocontenido, ver comentario en el propio archivo sobre la simplificación frente al `RoomSocketContext` de `plan.md` §8) + `LobbyPage`/`WaitingRoomPage`. Verificado: build/lint limpios, creación de sala confirmada end-to-end a través del proxy real de Vite (puerto 5173), y el hand-shake de SockJS (`/ws/info`) proxifica correctamente. La interacción visual en varias pestañas queda pendiente de que el usuario la abra en el navegador.

---

## Fase 3 — Fase de preguntas ✅ Completada (US-3)

- **T013. ✅** Endpoint `POST /api/rooms/{code}/questions` (recibe N textos = `questionsPerPlayer`, crea `Question` por jugador; valida que la sala esté en `COLLECTING_QUESTIONS`).
  *Depende de:* T010. *Cubre:* US-3. *Hecho cuando:* un jugador puede enviar sus preguntas y no puede reenviar/editar tras confirmarlas.
  *Nota:* verificado con curl: 409 si la sala aún está en `WAITING`, 400 si el número de preguntas no coincide con `questionsPerPlayer`, 200 al enviar correctamente, 409 al reenviar.

- **T014. ✅** Lógica de transición: cuando todos los `RoomPlayer` de la sala han enviado sus preguntas, el backend asigna `playOrder` aleatorio a todas las `Question` y pasa `Room.status = IN_PROGRESS`, arrancando la primera ronda (ver Fase 4).
  *Depende de:* T013. *Cubre:* US-3. *Hecho cuando:* al enviar la última pregunta pendiente, la sala arranca automáticamente sin acción manual del host.
  *Nota:* la creación de la primera `Round` (fase ANSWERING) queda para T016 (Fase 4, marcado con TODO en `QuestionService`); aquí solo se deja `Room.status = IN_PROGRESS` con las preguntas ya barajadas. `Room.startedAt` se redefinió para marcar este momento (cuando arranca de verdad la partida) en vez de cuando el host pulsa "Iniciar" (eso solo abre la fase de preguntas). Verificado con curl: con 3 jugadores y `questionsPerPlayer=2`, la sala permanece en `COLLECTING_QUESTIONS` tras las dos primeras entregas y pasa a `IN_PROGRESS` exactamente al recibir la última.

- **T015. [P] ✅** Pantalla React de envío de preguntas (formulario con N campos) + indicador de "esperando a los demás" tras enviar.
  *Depende de:* T007, T012. *Cubre:* US-3. *Hecho cuando:* un jugador ve confirmación de envío y la app le informa cuando arranca la partida.
  *Nota:* `QuestionSubmissionPage` en `/rooms/:code/questions`; `WaitingRoomPage` redirige aquí en cuanto la sala deja `WAITING`. Muestra un placeholder cuando `status` pasa a `IN_PROGRESS` (el motor de rondas real es la Fase 4). Build y lint limpios.

---

## Fase 4 — Motor de rondas: responder (US-4)

- **T016.** `GameEngineService`: al entrar en `IN_PROGRESS` (o al resolver una ronda), selecciona la siguiente `Question` no `discarded` según `playOrder`, crea `Round` en `ANSWERING`, calcula `answeringEndsAt` (+60s) y programa el timer (`TaskScheduler`).
  *Depende de:* T014, T004. *Cubre:* US-4. *Hecho cuando:* al iniciar la partida se crea la primera ronda con temporizador activo.

- **T017.** Handler WS `/app/rooms/{code}/answer`: guarda `Answer` del jugador (una por ronda), valida fase `ANSWERING` y que no haya respondido ya.
  *Depende de:* T016. *Cubre:* US-4. *Hecho cuando:* cada jugador puede enviar su respuesta una sola vez por ronda.

- **T018.** Cierre de fase `ANSWERING`: al cumplirse el timer o al responder el último jugador activo, se cierra la fase (sin más respuestas admitidas) y se dispara la Fase 5.
  *Depende de:* T017. *Cubre:* US-4. *Hecho cuando:* la fase se cierra tanto por timeout como por respuesta completa de todos, sin condición de carrera (última respuesta y timeout casi simultáneos).

- **T019.** Evento WS `ROUND_ANSWERING_STARTED` (pregunta + `answeringEndsAt`) emitido a toda la sala al crear cada ronda.
  *Depende de:* T016, T011. *Hecho cuando:* todos los clientes ven la misma pregunta y cuenta atrás sincronizada.

- **T020. [P]** Pantalla React "Responder": muestra pregunta, input de texto libre, cuenta atrás basada en `answeringEndsAt` (no en segundos relativos, `plan.md` §5).
  *Depende de:* T007, T012, T019. *Cubre:* US-4. *Hecho cuando:* el jugador envía su respuesta y ve deshabilitado el formulario tras enviar o al expirar el tiempo.

---

## Fase 5 — Motor de rondas: apostar (US-5)

- **T021.** Al cerrar `ANSWERING`: filtrar `Answer` con `text != null`; si no hay ninguna, resolver la ronda como "sin ganador" (regla añadida en `plan.md` §3) y saltar a Fase 6 con reparto nulo. Si hay al menos una, sortear una al azar → `Round.selectedAnswerId`, `authorPlayerId`; pasar a `BETTING` con `bettingEndsAt` (+30s).
  *Depende de:* T018. *Cubre:* US-5. *Hecho cuando:* se verifica con pruebas que el sorteo es aleatorio y excluye respuestas vacías.

- **T022.** Handler WS `/app/rooms/{code}/bet`: guarda `Bet` (valida fase `BETTING`, bettor ≠ autor, bettor no eliminado, `1 ≤ amount ≤ saldoActual`, un candidato único, sin apuesta previa en la ronda). Descuenta la apuesta del saldo del jugador en el momento de apostar (`plan.md` §6, nota de implementación).
  *Depende de:* T021. *Cubre:* US-5. *Hecho cuando:* apuestas inválidas (autor, importe fuera de rango, doble apuesta) son rechazadas con mensaje claro.

- **T023.** Cierre de fase `BETTING`: al cumplirse el timer, a todo jugador activo (no autor, no eliminado) que no haya apostado se le asigna automáticamente la apuesta mínima (1 ficha) sobre un candidato aleatorio (`autoAssigned = true`), tal como exige US-5. Luego dispara la Fase 6.
  *Depende de:* T022. *Cubre:* US-5. *Hecho cuando:* un jugador que no apuesta a tiempo aparece con una apuesta automática registrada.

- **T024.** Evento WS `ROUND_BETTING_STARTED` (respuesta mostrada, lista de candidatos, `bettingEndsAt`), enviado a todos menos implícitamente accionable solo para no-autores; al autor se le indica su rol de "espera" vía el propio evento (el cliente decide la vista según `myPlayerId == round.authorPlayerId`).
  *Depende de:* T021, T011. *Hecho cuando:* el autor ve pantalla de espera y el resto ve el formulario de apuesta.

- **T025. [P]** Pantalla React "Apostar": respuesta mostrada, selector de candidato (jugadores de la sala excepto uno mismo), input/slider de cantidad (máximo = saldo propio), cuenta atrás. Pantalla de espera alternativa para el autor.
  *Depende de:* T007, T012, T024. *Cubre:* US-5. *Hecho cuando:* un jugador apuesta y ve confirmación; el autor no ve nunca el formulario de apuesta en su propia ronda.

---

## Fase 6 — Resolución económica y eliminación (US-6, US-7)

- **T026.** Implementar `GameEngineService.resolveRound()` exactamente según el pseudocódigo de `plan.md` §6 (bote completo al autor si nadie acierta / reparto a partes iguales + bonus +2 por fallo si hay mezcla / solo recuperación si todos aciertan; redondeo `floor`).
  *Depende de:* T023. *Cubre:* US-6. *Hecho cuando:* tests unitarios cubren los 3 casos (nadie acierta / mezcla / todos aciertan) con los saldos exactos esperados.

- **T027.** Marcar `RoomPlayer.eliminated = true` cuando `saldoFichas <= 0` tras resolver; excluir jugadores eliminados de futuras fases `ANSWERING`/`BETTING` (siguen recibiendo eventos, no pueden accionar).
  *Depende de:* T026. *Cubre:* US-7. *Hecho cuando:* un jugador eliminado dejado sin saldo no puede volver a responder ni apostar, pero sigue viendo la partida.

- **T028.** Regla de pregunta descartada: si el autor de la siguiente `Question` en `playOrder` está `eliminated` en el momento de programarla, marcarla `discarded = true` y pasar directamente a la siguiente (spec §6/§9).
  *Depende de:* T027, T016. *Cubre:* US-7. *Hecho cuando:* la pregunta de un jugador eliminado nunca llega a jugarse y la partida no se bloquea.

- **T029.** Evento WS `ROUND_RESOLVED` (autor revelado, detalle de apuestas con acierto/fallo, saldos actualizados, lista de recién eliminados).
  *Depende de:* T026, T011. *Hecho cuando:* todos los clientes reciben el mismo resultado consistente tras cada ronda.

- **T030. [P]** Pantalla React "Resultado de ronda": revela autor, marca aciertos/fallos por jugador, anima/actualiza marcador de fichas, aviso de eliminación si aplica.
  *Depende de:* T007, T012, T029. *Cubre:* US-6, US-7. *Hecho cuando:* el jugador entiende de un vistazo qué ganó/perdió y por qué.

---

## Fase 7 — Fin de partida, historial y ranking (US-8)

- **T031.** Al no quedar más `Question` no descartadas: `Room.status = FINISHED`; calcular clasificación final por `saldoFichas`, determinar ganador(es) compartidos en empate (spec §9); persistir un `GameResult` por jugador.
  *Depende de:* T026, T028. *Cubre:* US-8. *Hecho cuando:* al terminar la última ronda válida se generan los `GameResult` correctos, incluidos empates con varios ganadores.

- **T032.** Evento WS `GAME_FINISHED` (clasificación final, ganadores).
  *Depende de:* T031, T011. *Hecho cuando:* todos los clientes reciben la pantalla final simultáneamente.

- **T033.** Cancelación de partida: si el host se desconecta con `Room.status = IN_PROGRESS`, marcar `CANCELLED` y emitir `GAME_CANCELLED` (spec §9); no se genera `GameResult`.
  *Depende de:* T006 (para detectar desconexión vía evento de sesión STOMP), T016. *Cubre:* spec §9. *Hecho cuando:* al cerrar la pestaña del host en partida, el resto recibe el aviso de cancelación de inmediato.

- **T034.** Endpoints `GET /api/users/me/history` y `GET /api/ranking/global` (agregación `SUM(finalFichas)` por usuario, `plan.md` §2).
  *Depende de:* T031. *Cubre:* US-8. *Hecho cuando:* ambos endpoints devuelven datos correctos tras varias partidas de prueba.

- **T035. [P]** Pantallas React: Resultados finales (clasificación + ganador/es), Historial personal, Ranking global.
  *Depende de:* T007, T032, T034. *Cubre:* US-8. *Hecho cuando:* un usuario puede ver su historial y su posición en el ranking tras jugar varias partidas.

---

## Fase 8 — Endurecimiento y pruebas transversales

- **T036.** Tests de integración del flujo completo (3 jugadores simulados vía cliente STOMP de test): crear sala → preguntas → N rondas → fin de partida, verificando saldos finales contra cálculo manual.
  *Depende de:* T031. *Hecho cuando:* el test pasa de forma determinista (mockear el sorteo aleatorio y el timeout donde aplique).

- **T037. [P]** Manejo de errores y validaciones de borde: sala llena/código inexistente, unirse a partida ya iniciada, apostar dos veces, apostar por uno mismo, enviar más/menos preguntas de las configuradas.
  *Depende de:* T013, T022. *Hecho cuando:* cada caso devuelve un error controlado (HTTP 4xx o frame de error WS), no una excepción no gestionada.

- **T038. [P]** Revisión de accesibilidad y responsive básico de las pantallas de ronda (son las de mayor uso durante la partida).
  *Depende de:* T020, T025, T030. *Hecho cuando:* las 3 pantallas de ronda son usables en móvil y de escritorio.

- **T039.** Documentar cómo levantar el proyecto en local (backend + frontend + base de datos) en un `README.md` de raíz.
  *Depende de:* T001, T002. *Hecho cuando:* alguien que no ha tocado el proyecto puede arrancarlo siguiendo el README.

---

## Resumen de trazabilidad (Historia de usuario → Tareas)

| Historia | Tareas |
|---|---|
| US-1 (crear/unirse) | T008, T009, T010, T011, T012 |
| US-2 (configurar partida) | T008, T010, T012 |
| US-3 (introducir preguntas) | T013, T014, T015 |
| US-4 (responder) | T016–T020 |
| US-5 (apostar) | T021–T025 |
| US-6 (resolución/reparto) | T026, T029, T030 |
| US-7 (eliminación) | T027, T028, T030 |
| US-8 (fin de partida/ranking) | T031–T035 |

## Siguiente paso

Ejecutar las fases en orden (0 → 8). Cada fase es un incremento demostrable: al terminar la Fase 5, por ejemplo, ya existe una partida jugable de principio a fin salvo el reparto económico fino (que llega en la Fase 6). Esto permite validar con usuarios reales antes de cerrar el resto del alcance.
