import { Platform } from "react-native";

import { storage } from "@/src/utils/storage";

const BASE = process.env.EXPO_PUBLIC_BACKEND_URL;
export const TOKEN_KEY = "kabadi.token";
export const USER_KEY = "kabadi.user";

// EXPO_PUBLIC_BACKEND_URL is inlined into the JS bundle at bundle-build time
// (by `eas build` for the native shell, or by `expo export`/`eas update` for
// an OTA publish) — it is NOT read at runtime on the device. If whoever ran
// that bundling step had no (or a stale) EXPO_PUBLIC_BACKEND_URL in their
// environment, BASE silently bakes in as undefined and every request below
// becomes a fetch to the literal string "undefined/api/...", which React
// Native's fetch polyfill reports as a generic, undiagnosable "Network
// request failed" with no indication why. Fail loudly and specifically
// instead, once, right away.
if (!BASE) {
  const msg =
    "EXPO_PUBLIC_BACKEND_URL is not set in this build — it was not present " +
    "when this JS bundle was built (native `eas build` or OTA `eas update`). " +
    "All API requests will fail. Check eas.json's build.<profile>.env for a " +
    "native build, or the local .env used when `eas update` was run for an " +
    "OTA publish.";
  console.error(msg);
}

export type ApiError = { status: number; message: string; detail?: any };

async function getToken(): Promise<string | null> {
  return await storage.secureGet<string | null>(TOKEN_KEY, null);
}

async function request<T = any>(
  method: string,
  path: string,
  body?: any,
  auth = true,
): Promise<T> {
  if (!BASE) {
    const err: ApiError = {
      status: 0,
      message: "App is misconfigured: no backend URL was built into this app version. Reinstall the latest build.",
    };
    throw err;
  }
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (auth) {
    const token = await getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(`${BASE}/api${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  let data: any = null;
  const text = await res.text();
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    const detail = data?.detail ?? data;
    const message =
      typeof detail === "string"
        ? detail
        : detail?.message || `Request failed (${res.status})`;
    const err: ApiError = { status: res.status, message, detail };
    throw err;
  }
  return data as T;
}

export const api = {
  get: <T = any>(path: string) => request<T>("GET", path),
  post: <T = any>(path: string, body?: any, auth = true) =>
    request<T>("POST", path, body, auth),
  patch: <T = any>(path: string, body?: any) => request<T>("PATCH", path, body),
  del: <T = any>(path: string) => request<T>("DELETE", path),
  base: BASE,
  getToken,
};

// Build an authenticated image URL for <Image> (web can't send headers).
export async function fileUrl(path: string): Promise<string> {
  const token = await getToken();
  return `${BASE}/api/files/${path}?token=${token}`;
}

// Multipart upload that works on both native and web.
export async function uploadImage(uri: string, name = "photo.jpg"): Promise<{ path: string; url: string }> {
  const token = await getToken();
  const form = new FormData();
  if (Platform.OS === "web") {
    const blob = await (await fetch(uri)).blob();
    form.append("file", blob, name);
  } else {
    form.append("file", { uri, name, type: "image/jpeg" } as any);
  }
  const res = await fetch(`${BASE}/api/upload`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) throw { status: res.status, message: "Upload failed" } as ApiError;
  return await res.json();
}
