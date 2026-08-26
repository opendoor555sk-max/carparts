import React, { createContext, useContext, useCallback, useState, useRef } from "react";
import { Animated, StyleSheet, Text, View } from "react-native";
import { colors, radius, spacing, font } from "@/src/theme";

type ToastType = "success" | "error" | "info" | "warning";
type ToastCtx = { show: (msg: string, type?: ToastType) => void };
const Ctx = createContext<ToastCtx>({ show: () => {} });
export const useToast = () => useContext(Ctx);

const bg: Record<ToastType, string> = {
  success: colors.success,
  error: colors.error,
  info: colors.surface3,
  warning: colors.warning,
};
const fg: Record<ToastType, string> = {
  success: colors.onSuccess,
  error: colors.onError,
  info: colors.onSurface,
  warning: colors.onWarning,
};

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [msg, setMsg] = useState("");
  const [type, setType] = useState<ToastType>("info");
  const opacity = useRef(new Animated.Value(0)).current;
  const timer = useRef<any>(null);

  const show = useCallback(
    (m: string, t: ToastType = "info") => {
      setMsg(m);
      setType(t);
      if (timer.current) clearTimeout(timer.current);
      Animated.timing(opacity, { toValue: 1, duration: 180, useNativeDriver: true }).start();
      timer.current = setTimeout(() => {
        Animated.timing(opacity, { toValue: 0, duration: 220, useNativeDriver: true }).start();
      }, 2600);
    },
    [opacity],
  );

  return (
    <Ctx.Provider value={{ show }}>
      {children}
      <Animated.View pointerEvents="none" style={[styles.wrap, { opacity }]}>
        <View style={[styles.toast, { backgroundColor: bg[type] }]} testID="app-toast">
          <Text style={[styles.text, { color: fg[type] }]}>{msg}</Text>
        </View>
      </Animated.View>
    </Ctx.Provider>
  );
}

const styles = StyleSheet.create({
  wrap: { position: "absolute", top: 60, left: 0, right: 0, alignItems: "center", zIndex: 9999 },
  toast: {
    maxWidth: "90%",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderRadius: radius.md,
  },
  text: { fontSize: font.base, fontWeight: "700" },
});
