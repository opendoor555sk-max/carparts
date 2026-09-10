import { useCallback, useEffect, useRef, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSequence, withTiming } from "react-native-reanimated";
import { Ionicons } from "@expo/vector-icons";
import { CameraView, useCameraPermissions } from "expo-camera";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";
import * as Location from "expo-location";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Field, Header } from "@/src/components/ui";
import { extractPartNumber } from "@/src/utils/barcode";
import { colors, font, radius, spacing } from "@/src/theme";
import { useAuth } from "@/src/context/AuthContext";

// Top-left total-quantity counter: 3x the old header-subtitle size (font.sm=12) is its
// new resting size. Each scan zooms it up to ZOOM_PEAK_SCALE then settles at 50% of
// that peak (i.e. smaller than the peak, not back to 1x) until the next scan.
const COUNTER_BASE_SIZE = font.sm * 3;
const ZOOM_PEAK_SCALE = 1.6;
const ZOOM_SETTLE_SCALE = ZOOM_PEAK_SCALE * 0.5;

export default function BatchBuy() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { company = "All" } = useLocalSearchParams<{ company: string }>();
  const { show } = useToast();
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "super_admin";
  const [permission, requestPermission] = useCameraPermissions();
  const [manual, setManual] = useState("");
  const [counts, setCounts] = useState<{ pn: string; qty: number }[]>([]);
  const [total, setTotal] = useState(0);
  const [gps, setGps] = useState("");
  const busy = useRef(false);
  const lastScan = useRef<{ code: string; at: number }>({ code: "", at: 0 });

  // Scan-success flash border + counter zoom, both driven from one trigger.
  const flashOpacity = useSharedValue(0);
  const counterScale = useSharedValue(1);
  const flashStyle = useAnimatedStyle(() => ({ opacity: flashOpacity.value }));
  const counterStyle = useAnimatedStyle(() => ({ transform: [{ scale: counterScale.value }] }));

  const triggerScanFeedback = useCallback(() => {
    flashOpacity.value = withSequence(
      withTiming(1, { duration: 70, easing: Easing.out(Easing.quad) }),
      withTiming(0, { duration: 420, easing: Easing.in(Easing.quad) }),
    );
    counterScale.value = withSequence(
      withTiming(ZOOM_PEAK_SCALE, { duration: 130, easing: Easing.out(Easing.quad) }),
      withTiming(ZOOM_SETTLE_SCALE, { duration: 260, easing: Easing.inOut(Easing.quad) }),
    );
    // expo-haptics has no numeric duration/intensity knob (iOS/Android don't expose
    // one through this API) — so "+50% duration/intensity" is approximated with a
    // second, heavier pulse shortly after the first, extending both the felt
    // duration and strength of the combined haptic event.
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy), 60);
  }, [flashOpacity, counterScale]);

  useEffect(() => {
    let sub: Location.LocationSubscription | null = null;
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== "granted") return;
        // initial fix
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
        setGps(`${loc.coords.latitude.toFixed(5)}, ${loc.coords.longitude.toFixed(5)}`);
        // keep GPS live so the correct purchase location is always current
        sub = await Location.watchPositionAsync(
          { accuracy: Location.Accuracy.High, distanceInterval: 3, timeInterval: 4000 },
          (l) => setGps(`${l.coords.latitude.toFixed(5)}, ${l.coords.longitude.toFixed(5)}`),
        );
      } catch {}
    })();
    return () => {
      try {
        if (sub) sub.remove();
      } catch {}
    };
  }, []);

  const addOne = useCallback(
    async (raw: string) => {
      const pn = extractPartNumber(raw);
      if (!pn || busy.current) return;
      busy.current = true;
      try {
        await api.post("/buy", { part_number: pn, company, condition: "Unknown", location: { gps }, override: false });
        triggerScanFeedback();
        setCounts((prev) => {
          const i = prev.findIndex((c) => c.pn === pn);
          if (i >= 0) {
            const cp = [...prev];
            cp[i] = { ...cp[i], qty: cp[i].qty + 1 };
            return cp;
          }
          return [{ pn, qty: 1 }, ...prev];
        });
        setTotal((t) => t + 1);
      } catch (e: any) {
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
        if (e?.detail?.code === "LIMIT_REACHED") {
          show(`🚫 ${pn} — Limit reached, DO NOT BUY`, "error");
        } else {
          show(e?.detail?.message || e?.message || "Add failed", "error");
        }
      } finally {
        setTimeout(() => (busy.current = false), 350);
      }
    },
    [company, gps, show, triggerScanFeedback],
  );

  const onBarcode = useCallback(
    ({ data }: { data: string }) => {
      const now = Date.now();
      if (data === lastScan.current.code && now - lastScan.current.at < 900) return;
      lastScan.current = { code: data, at: now };
      addOne(data);
    },
    [addOne],
  );

  return (
    <View style={styles.flex}>
      <Header title="Multiple Buy" subtitle={isSuperAdmin ? (gps ? "📍 GPS ✓" : "GPS…") : undefined} onBack={() => router.back()} />
      <View style={styles.cam}>
        {permission?.granted ? (
          <CameraView
            style={StyleSheet.absoluteFill}
            facing="back"
            barcodeScannerSettings={{ barcodeTypes: ["qr", "ean13", "code128", "code39", "upc_a", "datamatrix", "pdf417"] }}
            onBarcodeScanned={onBarcode}
          />
        ) : (
          <View style={styles.center}>
            <Ionicons name="camera" size={40} color={colors.brand} />
            <Text style={styles.dim}>Allow camera for quick batch scanning</Text>
            <Button title="Allow Camera" onPress={requestPermission} icon="camera" testID="batch-grant" />
          </View>
        )}
        <Animated.View style={[styles.flashOverlay, flashStyle]} pointerEvents="none" testID="batch-scan-flash" />
        <View style={styles.overlay} pointerEvents="none">
          <View style={styles.bracket} />
          <Text style={styles.hint}>Scan → each scan = +1 qty</Text>
        </View>
        <View style={styles.counterWrap} pointerEvents="none" testID="batch-total-counter">
          <Text style={styles.counterLabel}>TOTAL</Text>
          <Animated.Text style={[styles.counterValue, counterStyle]}>{total}</Animated.Text>
        </View>
      </View>

      <View style={styles.inputRow}>
        <View style={{ flex: 1 }}>
          <Field value={manual} onChangeText={setManual} placeholder="Manual part no. +1" autoCapitalize="characters" onSubmitEditing={() => { addOne(manual); setManual(""); }} returnKeyType="done" testID="batch-manual" />
        </View>
        <Pressable style={styles.addBtn} onPress={() => { addOne(manual); setManual(""); }} testID="batch-add"><Ionicons name="add" size={24} color={colors.onBrand} /></Pressable>
      </View>

      {isSuperAdmin ? (
        <View style={styles.gpsStrip}>
          <Ionicons name="location" size={16} color={gps ? colors.success : colors.warning} />
          <Text style={[styles.gpsStripText, { color: gps ? colors.success : colors.warning }]} numberOfLines={1}>
            {gps ? `Live GPS: ${gps}` : "Getting GPS…"}
          </Text>
        </View>
      ) : null}

      <FlatList
        data={counts}
        keyExtractor={(c) => c.pn}
        contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: insets.bottom + 90 }}
        ListEmptyComponent={<Text style={styles.empty}>Nothing scanned yet</Text>}
        renderItem={({ item }) => (
          <View style={styles.row} testID={`batch-${item.pn}`}>
            <Text style={styles.pn} numberOfLines={2}>{item.pn}</Text>
            <View style={styles.qtyBadge}><Text style={styles.qtyText}>x{item.qty}</Text></View>
          </View>
        )}
      />
      <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
        <Button title={`Done — ${total} units added`} onPress={() => router.replace("/(tabs)/inventory" as any)} icon="checkmark-circle" testID="batch-done" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cam: { height: 280, backgroundColor: "#000" },
  center: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.md, padding: spacing.lg },
  dim: { color: colors.info, textAlign: "center" },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center", gap: spacing.md },
  // Box +30% (220x120 -> 286x156); border 5x thinner than the prior 30 -> 6.
  bracket: { width: 286, height: 156, borderWidth: 6, borderColor: colors.success, borderRadius: radius.md },
  hint: { color: "#fff", fontWeight: "700", fontSize: font.base },
  flashOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.success },
  counterWrap: {
    position: "absolute",
    top: spacing.sm,
    left: spacing.sm,
    backgroundColor: "rgba(0,0,0,0.55)",
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    alignItems: "flex-start",
  },
  counterLabel: { color: "#fff", fontSize: font.sm, fontWeight: "700", letterSpacing: 1, opacity: 0.85 },
  counterValue: { color: colors.success, fontSize: COUNTER_BASE_SIZE, lineHeight: COUNTER_BASE_SIZE * 1.05, fontWeight: "900" },
  inputRow: { flexDirection: "row", gap: spacing.sm, padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.divider },
  addBtn: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  empty: { color: colors.info, textAlign: "center", marginTop: spacing.xl },
  gpsStrip: { flexDirection: "row", alignItems: "center", gap: spacing.xs, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, backgroundColor: colors.surface2, borderBottomWidth: 1, borderBottomColor: colors.divider },
  gpsStripText: { fontSize: font.base, fontWeight: "700", flex: 1 },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingHorizontal: spacing.lg, paddingVertical: spacing.lg },
  pn: { color: colors.onSurface, fontSize: 40, lineHeight: 46, fontWeight: "900", letterSpacing: 1, flex: 1 },
  qtyBadge: { backgroundColor: colors.success, borderRadius: radius.md, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, minWidth: 64, alignItems: "center" },
  qtyText: { color: colors.onSuccess, fontWeight: "900", fontSize: 30 },
  bar: { position: "absolute", bottom: 0, left: 0, right: 0, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
});
