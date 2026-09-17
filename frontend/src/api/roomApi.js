import { apiFetch } from './httpClient';

export function createRoom(questionsPerPlayer) {
  return apiFetch('/rooms', {
    method: 'POST',
    body: JSON.stringify({ questionsPerPlayer }),
  });
}

export function joinRoom(code) {
  return apiFetch(`/rooms/${code}/join`, { method: 'POST' });
}

export function getRoom(code) {
  return apiFetch(`/rooms/${code}`);
}

export function startRoom(code) {
  return apiFetch(`/rooms/${code}/start`, { method: 'POST' });
}

export function closeRoom(code) {
  return apiFetch(`/rooms/${code}`, { method: 'DELETE' });
}

export function submitQuestions(code, questions) {
  return apiFetch(`/rooms/${code}/questions`, {
    method: 'POST',
    body: JSON.stringify({ questions }),
  });
}
