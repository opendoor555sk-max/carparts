import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import * as LocalAuthentication from "expo-local-authentication";
import { Platform } from "react-native";
import { useRouter } from "expo-router";

import { api, TOKEN_KEY, USER_KEY } from "@/src/api/client";
import { storage } from "@/src/utils/storage";

export type User = {
  id: string;
  name: string;
  username: string;
  role: "admin" | "staff" | "super_admin";
  store_id?: string | null;
  store_name?: string;
  store_gst?: string;
  store_phone?: string;
  store_address?: string;
  store_logo?: string;
  store_bank?: string;
  permissions: string[];
  disabled?: boolean;
};

export type RegisterPayload = {
  store_name: string;
  name: string;
  username: string;
  password: string;
  contact: string;
};

type AuthState = {
  user: User | null;
  loading: boolean;
  hasStoredToken: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  biometricUnlock: () => Promise<boolean>;
  logout: () => Promise<void>;
  can: (perm: string) => boolean;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthState>({} as AuthState);
export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [hasStoredToken, setHasStoredToken] = useState(false);

  useEffect(() => {
    (async () => {
      const token = await storage.secureGet<string | null>(TOKEN_KEY, null);
      const cached = await storage.getItem<User | null>(USER_KEY, null);
      setHasStoredToken(!!token);
      if (token && cached) {
        // Validate silently against backend; keep cached user for instant UI.
        setUser(cached as User);
        try {
          const fresh = await api.get<User>("/auth/me");
          setUser(fresh);
          await storage.setItem(USER_KEY, fresh as any);
        } catch {
          // token invalid/expired -> require login
          setUser(null);
        }
      }
      setLoading(false);
    })();
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.post<{ access_token: string; user: User }>(
      "/auth/login",
      { username, password },
      false,
    );
    await storage.secureSet(TOKEN_KEY, res.access_token);
    await storage.setItem(USER_KEY, res.user as any);
    setHasStoredToken(true);
    setUser(res.user);
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    const res = await api.post<{ access_token: string; user: User }>(
      "/auth/register",
      payload,
      false,
    );
    await storage.secureSet(TOKEN_KEY, res.access_token);
    await storage.setItem(USER_KEY, res.user as any);
    setHasStoredToken(true);
    setUser(res.user);
  }, []);

  const biometricUnlock = useCallback(async (): Promise<boolean> => {
    if (Platform.OS === "web") return false;
    try {
      const hasHardware = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      if (!hasHardware || !enrolled) return false;
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: "Unlock with Fingerprint",
        cancelLabel: "Cancel",
      });
      if (!result.success) return false;
      const cached = await storage.getItem<User | null>(USER_KEY, null);
      if (cached) {
        setUser(cached as User);
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, []);

  const logout = useCallback(async () => {
    // storage never throws — a failed clear resolves `false` silently — so check
    // and retry once rather than assuming success. Also re-read the token back to
    // confirm it's actually gone before returning.
    let tokenCleared = await storage.secureRemove(TOKEN_KEY);
    if (!tokenCleared) tokenCleared = await storage.secureRemove(TOKEN_KEY);
    let userCleared = await storage.removeItem(USER_KEY);
    if (!userCleared) userCleared = await storage.removeItem(USER_KEY);
    const stillThere = await storage.secureGet<string | null>(TOKEN_KEY, null);
    if (stillThere !== null) {
      console.warn("[auth] logout: token still present after clear+retry");
    }
    setHasStoredToken(false);
    setUser(null);
    // The actual fix for "Sign Out doesn't show Login on reopen": nothing was
    // ever navigating away from whatever protected screen was on-screen when
    // logout() ran. app/index.tsx's <Redirect> only gets evaluated when the
    // router visits "/" — which never happened on its own, so a currently
    // focused (tabs) screen just kept rendering with a null user in memory.
    // This also means BackHandler.exitApp() on Android was never guaranteed
    // to matter here: it doesn't call Process.killProcess()/System.exit() —
    // it only invokes the native default back-press action (moveTaskToBack/
    // finish), so the process and all in-memory state can survive and simply
    // resume in the background. Navigate explicitly so the app is correct
    // whether or not the process actually dies.
    router.replace("/login");
  }, [router]);

  const refresh = useCallback(async () => {
    try {
      const fresh = await api.get<User>("/auth/me");
      setUser(fresh);
      await storage.setItem(USER_KEY, fresh as any);
    } catch {}
  }, []);

  const can = useCallback(
    (perm: string) => {
      if (!user) return false;
      if (user.role === "admin" || user.role === "super_admin") return true;
      return user.permissions?.includes(perm);
    },
    [user],
  );

  return (
    <AuthContext.Provider
      value={{ user, loading, hasStoredToken, login, register, biometricUnlock, logout, can, refresh }}
    >
      {children}
    </AuthContext.Provider>
  );
}
