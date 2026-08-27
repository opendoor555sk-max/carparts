import { Platform } from "react-native";

import { storage } from "@/src/utils/storage";

const BASE = process.env.EXPO_PUBLIC_BACKEND_URL;
export const TOKEN_KEY = "kabadi.token";
export const USER_KEY = "kabadi.user";

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
