import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { useEffect } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";

import { useIconFonts } from "@/src/hooks/use-icon-fonts";
import { AuthProvider } from "@/src/context/AuthContext";
import { ToastProvider } from "@/src/context/ToastContext";
import { LanguageProvider } from "@/src/context/LanguageContext";
import { LocationGate } from "@/src/components/LocationGate";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

// Expo Router's documented crash boundary: exporting a component named
// `ErrorBoundary` from a route file makes the router wrap that file's
// rendered tree in a real React error boundary (see expo-router's Try.js —
// getDerivedStateFromError + a `catch` render prop). Exported from the ROOT
// layout, this covers the entire app, including RootLayout's own render
// below, not just child screens.
//
// Before this, the app had NO error boundary anywhere — an uncaught render
// error at any point (this file, any screen, any provider) unmounted the
// whole tree with nothing to catch it. Production builds don't show a red
// screen/stack trace, so the visible symptom is exactly "blank white
// screen, no error" regardless of what actually threw. This turns that
// failure mode into a visible, recoverable screen instead.
//
// Deliberately self-contained: this renders INSTEAD OF everything below
// (Language/Auth/Toast providers included) when something throws, so it
// must not depend on any context those providers supply — if one of them
// is what crashed, its context isn't available here either. Also
// deliberately icon-font-free (a plain emoji glyph, not <Ionicons>): this
// file's own comment below notes vector-icon fonts can throw if an <Icon>
// mounts before the family registers — the one path that must never
// depend on that is the path shown when something has already gone wrong.
export function ErrorBoundary({ error, retry }: { error: Error; retry: () => void }) {
  return (
    <GestureHandlerRootView style={styles.flex}>
      <SafeAreaProvider>
        <View style={styles.errorWrap}>
          <Text style={styles.errorIcon}>⚠️</Text>
          <Text style={styles.errorTitle}>Something went wrong</Text>
          <Text style={styles.errorMessage}>
            {error?.message || "An unexpected error occurred."}
          </Text>
          <Pressable onPress={retry} style={styles.errorButton} testID="error-boundary-retry">
            <Text style={styles.errorButtonText}>Try Again</Text>
          </Pressable>
        </View>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

// TEMP: LogBox suppression disabled while debugging the post-login 404 —
// re-enable (LogBox.ignoreAllLogs(true)) once resolved.
// LogBox.ignoreAllLogs(true);

// Keep the native splash visible from cold start until icon fonts register.
// Required because @expo/vector-icons' componentDidMount fallback fires
// Font.loadAsync against a broken vendor path if any <Icon> mounts before
// the family is registered — which throws on Android Expo Go.
SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const [loaded, error] = useIconFonts();

  useEffect(() => {
    if (loaded || error) {
      SplashScreen.hideAsync();
    }
  }, [loaded, error]);

  // If the CDN is unreachable we fall through on error rather than wedging
  // the app — icons will tofu, but the app still boots.
  if (!loaded && !error) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1, backgroundColor: colors.surface }}>
      <SafeAreaProvider>
        <LanguageProvider>
          <AuthProvider>
            <ToastProvider>
              <StatusBar style="dark" />
              <View style={{ flex: 1, backgroundColor: colors.surface }}>
                <LocationGate>
                  <Stack
                    screenOptions={{
                      headerShown: false,
                      contentStyle: { backgroundColor: colors.surface },
                      animation: "slide_from_right",
                    }}
                  />
                </LocationGate>
              </View>
            </ToastProvider>
          </AuthProvider>
        </LanguageProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  errorWrap: { flex: 1, alignItems: "center", justifyContent: "center", padding: spacing.xl, gap: spacing.sm },
  errorIcon: { fontSize: 56 },
  errorTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", marginTop: spacing.sm, textAlign: "center" },
  errorMessage: { color: colors.info, fontSize: font.base, textAlign: "center", lineHeight: 20 },
  errorButton: {
    marginTop: spacing.lg,
    backgroundColor: colors.brand,
    borderRadius: radius.md,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.md,
    ...shadow.sm,
  },
  errorButtonText: { color: colors.onBrand, fontSize: font.lg, fontWeight: "800" },
});
