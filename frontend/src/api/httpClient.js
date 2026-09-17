// Cliente REST mínimo hacia el backend (plan.md §4, T007).
// El token JWT vive en memoria (ver AuthContext) y se inyecta aquí en cada
// petición; no se persiste en localStorage a propósito (MVP: al recargar la
// página hay que volver a iniciar sesión).

let authToken = null;

export function setAuthToken(token) {
  authToken = token;
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function apiFetch(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`);
  }

  const response = await fetch(`/api${path}`, { ...options, headers });

  if (!response.ok) {
    let message = `Error ${response.status}`;
    try {
      const data = await response.json();
      message = data.message || message;
    } catch {
      // La respuesta de error no traía cuerpo JSON; nos quedamos con el mensaje genérico.
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}
