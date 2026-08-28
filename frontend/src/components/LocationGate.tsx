import React, { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, AppState, AppStateStatus, Linking, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import * as Location from "expo-location";

import { colors, font, radius, spacing } from "@/src/theme";

type Status = "checking" | "services_off" | "denied" | "blocked" | "ok";

/**
 * Strict GPS gate. On native (phone) the app is UNUSABLE unless
 * device Location services are ON and foreground permission is granted.
 * Web (dev/preview only) passes through since browser iframes block geolocation.
 */
export function LocationGate({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>(Platform.OS === "web" ? "ok" : "checking");
  const [busy, setBusy] = useState(false);
  const appState = useRef(AppState.currentState);

  const check = useCallback(async (allowAsk = true) => {
    if (Platform.OS === "web") {
      setStatus("ok");
      return;
    }
    setBusy(true);
    try {
      const servicesOn = await Location.hasServicesEnabledAsync();
      if (!servicesOn) {
        setStatus("services_off");
        return;
      }
      let perm = await Location.getForegroundPermissionsAsync();
      if (!perm.granted && perm.canAskAgain && allowAsk) {
        perm = await Location.requestForegroundPermissionsAsync();
      }
      if (perm.granted) {
        // Confirm a real fix is obtainable; if it throws, treat as services off.
        try {
          await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        } catch {
          setStatus("services_off");
          return;
        }
        setStatus("ok");
      } else if (!perm.canAskAgain) {
        setStatus("blocked");
      } else {
        setStatus("denied");
      }
    } catch {
      setStatus("services_off");
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    check(true);
    const sub = AppState.addEventListener("change", (next: AppStateStatus) => {
      if (appState.current.match(/inactive|background/) && next === "active" && status !== "ok") {
        check(false);
      }
      appState.current = next;
    });
    return () => sub.remove();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (status === "ok") return <>{children}</>;

  const config: Record<Exclude<Status, "ok">, { title: string; msg: string; primary: string; onPrimary: () => void }> = {
    checking: {
      title: "Checking Location…",
      msg: "Please wait while we verify GPS access.",
      primary: "Retry",
      onPrimary: () => check(true),
    },
    services_off: {
      title: "Turn On GPS / Location",
      msg: "This app works only when your device Location (GPS) is turned ON. Please enable it to continue.",
      primary: "Open Location Settings",
      onPrimary: () => Linking.openSettings(),
    },
    denied: {
      title: "Location Permission Required",
      msg: "This app cannot run without location access. Please allow location to continue.",
      primary: "Allow Location",
      onPrimary: () => check(true),
    },
    blocked: {
      title: "Location Permission Blocked",
      msg: "Location is blocked in settings. Open Settings, allow Location for this app, then return.",
      primary: "Open Settings",
      onPrimary: () => Linking.openSettings(),
    },
  };

  const c = config[status];

  return (
    <View style={styles.wrap}>
      {status === "checking" ? (
        <ActivityIndicator size="large" color={colors.brand} />
      ) : (
        <Ionicons name="location" size={64} color={colors.brand} />
      )}
      <Text style={styles.title}>{c.title}</Text>
      <Text style={styles.msg}>{c.msg}</Text>
      {status !== "checking" ? (
        <>
          <Pressable style={styles.btn} onPress={c.onPrimary} disabled={busy} testID="gps-primary">
            <Text style={styles.btnText}>{c.primary}</Text>
          </Pressable>
          <Pressable style={styles.retry} onPress={() => check(true)} disabled={busy} testID="gps-retry">
            {busy ? <ActivityIndicator size="small" color={colors.brand} /> : <Text style={styles.retryText}>I have enabled it — Retry</Text>}
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: colors.surface, alignItems: "center", justifyContent: "center", padding: spacing.xl, gap: spacing.md },
  title: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", textAlign: "center", marginTop: spacing.md },
  msg: { color: colors.onSurface3, fontSize: font.base, textAlign: "center", lineHeight: 22, paddingHorizontal: spacing.md },
  btn: { backgroundColor: colors.brand, borderRadius: radius.md, paddingHorizontal: spacing.xl, paddingVertical: spacing.md, marginTop: spacing.lg, minWidth: 240, alignItems: "center" },
  btnText: { color: colors.onBrand, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  retry: { paddingVertical: spacing.md, minHeight: 44, justifyContent: "center" },
  retryText: { color: colors.brand, fontSize: font.base, fontWeight: "700" },
});
