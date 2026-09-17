# Especificación de Funcionalidad: "¿Quién lo dijo?" (nombre provisional)

**ID de feature:** 001-adivina-quien-respondio
**Estado:** Borrador listo para revisión (v0.1)
**Fecha:** 2026-09-17
**Metodología:** Spec-Driven Development (SDD) — este documento define el QUÉ y el POR QUÉ. El CÓMO (arquitectura, endpoints, esquema de BD) se desarrollará en `plan.md` como siguiente fase.

---

## 1. Resumen

Juego de fiesta multijugador online (3+ jugadores) en el que, en cada ronda, se muestra una pregunta introducida previamente por uno de los jugadores junto con una de las respuestas de texto libre enviadas por el grupo, elegida al azar y sin revelar su autor. El resto de jugadores (excepto quien la escribió) debe apostar fichas a quién creen que la ha respondido. Al resolverse la ronda se reparten fichas según los aciertos y fallos. Gana la partida quien más fichas tenga al finalizar el número de rondas configurado.

## 2. Contexto técnico (decidido, no forma parte del "qué" pero se registra aquí)

- **Frontend:** React (una sala por partida, actualización en vivo del estado del juego).
- **Backend:** Spring Boot.
- **Comunicación en tiempo real:** WebSocket (STOMP sobre SockJS) para sincronizar fases, temporizadores y resultados entre todos los jugadores de una sala.
- **Cuentas:** autenticación persistente (registro/login), no solo nickname de sesión.

## 3. Actores

- **Jugador registrado:** tiene cuenta, puede crear o unirse a salas, participa en las rondas.
- **Anfitrión:** el jugador que crea la sala; además de jugar, configura los parámetros de la partida (nº de preguntas por jugador, etc.).

## 4. Historias de usuario

### US-1 — Crear y unirse a una sala
Como jugador, quiero crear una sala de juego o unirme a una existente mediante un código, para poder jugar una partida con mis amigos.

**Criterios de aceptación:**
- Un jugador registrado puede crear una sala nueva, que genera un código/enlace de unión.
- Otros jugadores registrados pueden unirse a la sala introduciendo ese código, mientras la partida no haya comenzado.
- La sala requiere un mínimo de 3 jugadores para poder iniciarse; no tiene máximo estricto (probado hasta 8-10 jugadores).
- Solo el anfitrión puede iniciar la partida una vez cumplido el mínimo de jugadores.

### US-2 — Configurar la partida
Como anfitrión, quiero definir cuántas preguntas debe aportar cada jugador, para controlar la duración de la partida.

**Criterios de aceptación:**
- Antes de iniciar, el anfitrión define el número de preguntas que cada jugador debe introducir (mínimo 1).
- El número total de rondas de la partida = (nº de jugadores) × (preguntas por jugador).

### US-3 — Introducir preguntas
Como jugador, quiero escribir mis preguntas antes de que empiece la partida, para que se usen durante el juego.

**Criterios de aceptación:**
- Cada jugador debe introducir el número de preguntas configurado antes de que la partida pueda arrancar.
- La partida no comienza hasta que todos los jugadores han enviado sus preguntas.

### US-4 — Responder a la pregunta de una ronda
Como jugador, quiero escribir una respuesta de texto libre a la pregunta planteada en la ronda, para participar en el juego (incluso si la pregunta es la que yo mismo escribí).

**Criterios de aceptación:**
- Al iniciar una ronda, se muestra la pregunta a todos los jugadores simultáneamente.
- Cada jugador dispone de 60 segundos (por defecto) para enviar su respuesta en texto libre.
- Si un jugador no responde a tiempo, se le asigna una respuesta vacía/marcador de "sin respuesta" y no participa como candidato en esa ronda (ver Edge Cases).
- El autor de la pregunta de esa ronda también responde con normalidad (no la conoce de antemano en el sentido de identidad, solo la ha escrito).

### US-5 — Apostar por el autor de la respuesta mostrada
Como jugador, quiero ver una respuesta aleatoria de las enviadas y apostar fichas sobre quién creo que la escribió, para intentar ganar fichas si acierto.

**Criterios de aceptación:**
- Terminada la fase de respuestas, el sistema elige al azar una de las respuestas enviadas (con contenido, no vacías) y la muestra a todos sin revelar el autor.
- El jugador autor de esa respuesta es excluido de esta fase de apuesta (no ve las opciones de apuesta, solo espera el resultado).
- El resto de jugadores ven la lista de posibles candidatos (todos los jugadores de la partida excepto ellos mismos, ya que cada uno sabe que la respuesta mostrada no es la suya propia si no coincide con lo que escribió) y deben elegir **un único candidato**.
- Cada jugador debe apostar un mínimo de 1 ficha y como máximo su saldo actual disponible, dentro de 30 segundos (por defecto).
- Apostar es obligatorio: si el temporizador expira sin apuesta, se le asigna automáticamente la apuesta mínima (1 ficha) sobre un candidato aleatorio entre los disponibles (ver Edge Cases).

### US-6 — Resolución de la ronda y reparto de fichas
Como jugador, quiero ver quién era el verdadero autor y cómo han quedado las fichas de todos tras la ronda, para seguir la evolución de la partida.

**Criterios de aceptación:** (ver también sección 6, Reglas de negocio, para la fórmula exacta)
- Se revela el autor real de la respuesta.
- Los jugadores que apostaron por el autor correcto ("aciertan") recuperan su apuesta y además se reparten a partes iguales el bote de fichas perdidas por los que fallaron.
- Los jugadores que apostaron por un candidato incorrecto ("fallan") pierden la cantidad apostada.
- El autor de la respuesta recibe una bonificación por cada jugador que falló en adivinarlo; si **nadie** acierta, el autor se lleva además el bote completo de fichas falladas.
- Se actualiza y muestra el marcador (saldo de fichas) de todos los jugadores tras cada ronda.

### US-7 — Eliminación por falta de fichas
Como jugador que se ha quedado sin fichas, entiendo que quedo fuera de la posibilidad de seguir apostando, para que la partida siga siendo justa para el resto.

**Criterios de aceptación:**
- Si el saldo de un jugador llega a 0, no puede cumplir la apuesta mínima obligatoria (1 ficha) y pasa a estado "eliminado/espectador" para el resto de la partida.
- Un jugador eliminado sigue viendo la partida (preguntas, respuestas, resultados) pero no responde ni apuesta en rondas futuras. (Nota: si la pregunta de una ronda futura es la que él mismo introdujo, ver Edge Cases sobre preguntas de jugadores eliminados).

### US-8 — Fin de la partida y ranking
Como jugador, quiero ver quién ha ganado al terminar todas las rondas, y consultar mi historial de partidas, para ver mi progreso.

**Criterios de aceptación:**
- La partida termina cuando se han jugado todas las rondas configuradas (nº de jugadores × preguntas por jugador).
- Gana el jugador con más fichas al finalizar la última ronda.
- Se muestra una pantalla final con la clasificación de todos los jugadores por fichas finales.
- El resultado de la partida (posición, fichas finales, aciertos/fallos) queda guardado en el historial del jugador.
- Existe una pantalla de ranking global básico entre todos los usuarios registrados (ver sección 7).

## 5. Flujo del juego (paso a paso)

1. Anfitrión crea sala → define nº de preguntas por jugador → comparte código.
2. Jugadores se unen con su cuenta (mínimo 3).
3. Anfitrión inicia la partida (requiere mínimo de jugadores alcanzado).
4. Fase de preparación: cada jugador introduce sus preguntas (bloqueante: la partida no avanza hasta que todos han enviado las suyas).
5. Se determina el orden de las preguntas/rondas (aleatorio entre todas las preguntas recopiladas).
6. Por cada ronda:
   a. Se muestra la pregunta a todos → fase de respuesta (60s).
   b. Se elige una respuesta al azar entre las enviadas → fase de apuesta (30s) para todos menos el autor.
   c. Resolución: se revela el autor, se reparten fichas, se actualiza el marcador.
7. Al completar todas las rondas → pantalla de resultados finales y ranking de la partida.
8. Se persiste el resultado en el historial de cada jugador y se actualiza el ranking global.

## 6. Reglas de negocio (economía de fichas)

- **Saldo inicial:** 50 fichas por jugador.
- **Apuesta:** cantidad elegida libremente por el jugador entre 1 ficha (mínimo obligatorio) y su saldo actual (no puede apostar más de lo que tiene).
- **Resolución de una ronda**, siendo `bote_fallos` la suma de las apuestas de todos los jugadores que fallaron:
  - Si **al menos un jugador acierta** (y no todos):
    - Cada acertante recupera su propia apuesta.
    - `bote_fallos` se reparte a **partes iguales** entre todos los acertantes (además de recuperar su apuesta).
    - El autor recibe una bonificación fija de **+2 fichas por cada jugador que falló**.
  - Si **nadie acierta** (todos fallan o no hay apuestas válidas):
    - El autor se lleva íntegro el `bote_fallos` completo. **No** recibe además la bonificación fija de +2/fallo en este caso (regla única, sin duplicar recompensa).
  - Si **todos aciertan** (`bote_fallos` = 0): cada acertante simplemente recupera su apuesta, sin ganancia adicional. El autor no recibe bonificación (no hubo ningún fallo que bonificar).
- **Eliminación:** saldo en 0 ⇒ jugador pasa a espectador, no puede responder ni apostar en rondas restantes.
- **Preguntas de jugadores eliminados:** si un jugador queda eliminado antes de que le toque el turno de su propia pregunta, esa pregunta se descarta y la partida pasa directamente a la siguiente ronda (la partida se acorta en una ronda por cada pregunta descartada de esta forma).

## 7. Historial y ranking (alcance MVP)

- Por cada partida finalizada, se guarda por jugador: fecha, posición final, fichas finales, nº de aciertos, nº de fallos.
- Ranking global básico: listado de jugadores registrados ordenado por **fichas acumuladas históricas** (suma de las fichas finales obtenidas en cada partida jugada).
- **Fin de partida — empates:** si dos o más jugadores terminan con el mismo nº de fichas, se declaran **ganadores compartidos** (sin desempate adicional); ambos quedan registrados como ganadores de esa partida en el historial.
- **Anfitrión durante la partida:** una vez iniciada, el anfitrión no conserva ningún rol especial (juega como uno más). Si el anfitrión se desconecta durante la partida ya iniciada, **la partida se cancela para todos los jugadores** (no se guarda como partida finalizada en el historial, salvo el registro de "partida cancelada" si se desea auditar).

## 8. Fuera de alcance (no incluido en este MVP)

- Elegir cantidad de apuesta repartida entre varios candidatos (solo se permite un candidato por apuesta).
- Reconexión con recuperación de estado tras desconexión a mitad de ronda (el timeout automático ya cubre la ausencia; no se implementa lógica de "pausar" la partida).
- Preguntas generadas por IA o banco de preguntas predefinido (se deja como posible iteración futura).
- Configuración de duración de temporizadores por el anfitrión (quedan fijos: 60s respuesta / 30s apuesta).
- Chat en vivo, emotes o interacción social adicional durante la partida.
- Moderación de contenido de preguntas/respuestas (filtrado de lenguaje inapropiado).
- Aplicación móvil nativa (solo web).

## 9. Estado de las clarificaciones

Todas las ambigüedades detectadas en la v0.1 han sido resueltas y ya están incorporadas en las secciones 6 y 7:

| # | Punto | Resolución |
|---|---|---|
| 1 | Regla "nadie acierta" | El autor se lleva el bote completo; **no** se suma además la bonificación fija por fallo (regla única, sin duplicar). |
| 2 | Valor de la bonificación por fallo | **+2 fichas** por cada jugador que falla (cuando sí hay al menos un acierto). |
| 3 | Pregunta de jugador eliminado | Se **descarta** esa pregunta y se pasa directamente a la siguiente ronda. |
| 4 | Criterio del ranking global | **Fichas acumuladas históricas** (suma de fichas finales de todas las partidas jugadas). |
| 5 | Anfitrión durante la partida | Deja de tener rol especial al iniciar; si se desconecta, **la partida se cancela para todos**. |
| 6 | Empate en el marcador final | Se declaran **ganadores compartidos**, sin desempate adicional. |

La especificación funcional queda cerrada (sin `[NEEDS CLARIFICATION]` pendientes) y lista para avanzar a la fase de diseño técnico.

## 10. Glosario

- **Ficha:** unidad única que funciona simultáneamente como moneda de apuesta y como puntuación/marcador del jugador.
- **Ronda:** ciclo completo de: mostrar pregunta → respuestas → apuesta → resolución, asociado a una única pregunta.
- **Autor:** jugador cuya respuesta ha sido la seleccionada al azar para la ronda en curso.
- **Acertante / Fallo:** jugador cuya apuesta coincide o no con el autor real de la ronda.
- **Bote de fallos:** suma total de las fichas apostadas por los jugadores que fallaron en una ronda.

---

**Siguiente paso sugerido (SDD):** con la spec cerrada, generar `plan.md` (arquitectura Spring Boot + React, modelo de datos, contratos WebSocket/REST) y después `tasks.md` (desglose en tareas implementables).
