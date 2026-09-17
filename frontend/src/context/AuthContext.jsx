import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { loginUser, registerUser } from '../api/authApi';
import { setAuthToken } from '../api/httpClient';

const AuthContext = createContext(null);

/**
 * Sesión del usuario (spec.md §3, plan.md §7, T007).
 * El token se guarda solo en memoria (estado de React): al recargar la
 * página se pierde la sesión a propósito, es una simplificación del MVP
 * (ver tasks.md T007). Persistirla de forma más robusta queda para más adelante.
 */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(null); // { token, userId, username } | null

  const login = useCallback(async (username, password) => {
    const data = await loginUser(username, password);
    setAuthToken(data.token);
    setSession(data);
    return data;
  }, []);

  const register = useCallback(async (username, email, password) => {
    const data = await registerUser(username, email, password);
    setAuthToken(data.token);
    setSession(data);
    return data;
  }, []);

  const logout = useCallback(() => {
    setAuthToken(null);
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({
      user: session,
      token: session?.token ?? null,
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  }
  return context;
}
