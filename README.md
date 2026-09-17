# ¿Quién lo dijo?

Juego de fiesta multijugador (3+ jugadores): se lanza una pregunta, todos responden en texto libre, sale una respuesta al azar sin revelar su autor y el resto apuesta fichas a quién creen que la escribió. Se reparten fichas según aciertos y fallos; gana quien más fichas tenga al final de la partida.

Desarrollado siguiendo la metodología **Spec-Driven Development (SDD)**: primero la especificación funcional, luego el plan técnico, luego el desglose en tareas, y por último la implementación.

## Documentación (SDD)

Toda la especificación de la primera feature vive en [`specs/001-adivina-quien-respondio/`](specs/001-adivina-quien-respondio):

- [`spec.md`](specs/001-adivina-quien-respondio/spec.md) — Qué hace el juego: historias de usuario, reglas de negocio (economía de fichas), casos límite y fuera de alcance.
- [`plan.md`](specs/001-adivina-quien-respondio/plan.md) — Cómo se construye: arquitectura (Spring Boot + React + WebSocket STOMP), modelo de datos, máquina de estados, contratos de API/WebSocket.
- [`tasks.md`](specs/001-adivina-quien-respondio/tasks.md) — Desglose en tareas implementables, trazadas a cada historia de usuario.

## Stack

- **Backend:** Spring Boot (Web, Security, WebSocket, Data JPA)
- **Frontend:** React (Vite) + `@stomp/stompjs` sobre SockJS
- **Base de datos:** H2 en desarrollo, PostgreSQL en producción

## Estado del proyecto

En fase de especificación completada; implementación aún no iniciada (ver `tasks.md` para el orden de las fases).
