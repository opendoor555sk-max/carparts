import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { unstable_createElement } from "react-native-web";
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSequence, withTiming } from "react-native-reanimated";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BrowserMultiFormatReader } from "@zxing/browser";
import { BarcodeFormat, DecodeHintType } from "@zxing/library";
import * as Haptics from "expo-haptics";
import * as Location from "expo-location";
import { useAudioPlayer } from "expo-audio";

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

export default function BatchBuyWeb() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { company = "All" } = useLocalSearchParams<{ company: string }>();
  const { show } = useToast();
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "super_admin";
  const [manual, setManual] = useState("");
  // Draft/review list — nothing here has been sent to the backend yet.
  // Scanning only adds/increments a line; stock is written on confirmAndAddToStock().
  const [counts, setCounts] = useState<{ pn: string; qty: number }[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [gps, setGps] = useState("");
  const videoRef = useRef<any>(null);
  const controlsRef = useRef<any>(null);
  const busy = useRef(false);
  const last = useRef<{ c: string; at: number }>({ c: "", at: 0 });
  const total = useMemo(() => counts.reduce((s, c) => s + c.qty, 0), [counts]);

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

  // ---- Real-time (per-scan) limit warning — separate from the hard, ---
  // authoritative block that confirmAndAddToStock() already gets from /buy's
  // 409 LIMIT_REACHED. This is an early heads-up so a scanning operator sees
  // "stop" the instant they cross the line, instead of only finding out after
  // scanning a whole batch and hitting Confirm. Deliberately loud/jarring —
  // distinct in every channel (color, sound, haptic pattern, copy) from the
  // green "added" feedback above — so it can't be mistaken for a normal scan.
  const dangerFlashOpacity = useSharedValue(0);
  const dangerFlashStyle = useAnimatedStyle(() => ({ opacity: dangerFlashOpacity.value }));
  const [dangerBorder, setDangerBorder] = useState(false);
  const [stopMessage, setStopMessage] = useState<string | null>(null);
  const dangerTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  // A short, harsh hi-lo alarm — nothing else in this app plays a sound, so
  // this alone is enough to read as "different from the normal success beep".
  const dangerPlayer = useAudioPlayer(require("../assets/sounds/limit_reached.wav"));

  const triggerDangerFeedback = useCallback(
    (pn: string) => {
      dangerFlashOpacity.value = withSequence(
        withTiming(1, { duration: 60, easing: Easing.out(Easing.quad) }),
        withTiming(0.15, { duration: 180, easing: Easing.inOut(Easing.quad) }),
        withTiming(1, { duration: 60, easing: Easing.out(Easing.quad) }),
        withTiming(0, { duration: 500, easing: Easing.in(Easing.quad) }),
      );
      setDangerBorder(true);
      setStopMessage(`STOP BUYING ${pn} — Limit Reached`);
      if (dangerTimeout.current) clearTimeout(dangerTimeout.current);
      dangerTimeout.current = setTimeout(() => {
        setDangerBorder(false);
        setStopMessage(null);
      }, 2200);
      // Distinct double-buzz (vs. the single success pulse above) plus the alarm sound.
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy), 90);
      setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy), 260);
      try {
        dangerPlayer.seekTo(0);
        dangerPlayer.play();
      } catch {
        // best-effort — a missing/failed sound must never block the actual warning
      }
    },
    [dangerFlashOpacity, dangerPlayer],
  );

  // Live, per-scan check against GET /limits/{part_number} — the same
  // compute_limit() the Buy screen's limit card reads. `draftQtyBefore` is
  // how many of this part are ALREADY queued in this batch's draft (not yet
  // actually purchased), since those don't show up in the backend's
  // existing_stock yet but do count toward whether one more would go over.
  // Fails OPEN on a network error: the real, authoritative block still
  // happens at Confirm time via /buy's 409 LIMIT_REACHED, so a flaky
  // connection here degrades to "no early warning", never "can't scan".
  const checkUnitAgainstLimit = useCallback(
    async (pn: string, draftQtyBefore: number): Promise<boolean> => {
      try {
        const limit = await api.get(`/limits/${encodeURIComponent(pn)}`);
        if (!limit?.limit_enabled || limit.allowed_limit == null) return true;
        const projected = (limit.existing_stock || 0) + draftQtyBefore + 1;
        if (projected > limit.allowed_limit) {
          triggerDangerFeedback(pn);
          return false;
        }
        return true;
      } catch {
        return true;
      }
    },
    [triggerDangerFeedback],
  );

  useEffect(() => {
    let timer: any = null;
    let active = true;
    const fetchOnce = async () => {
      try {
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
        if (active) setGps(`${loc.coords.latitude.toFixed(5)}, ${loc.coords.longitude.toFixed(5)}`);
      } catch {}
    };
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== "granted") return;
        await fetchOnce();
        // web: poll instead of watchPositionAsync (subscription.remove() is unsupported on web)
        timer = setInterval(fetchOnce, 6000);
      } catch {}
    })();
    return () => {
      active = false;
      if (timer) clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    return () => {
      if (dangerTimeout.current) clearTimeout(dangerTimeout.current);
    };
  }, []);

  // Scanning is now purely local — it only adds/increments a draft line.
  // Nothing touches the backend until confirmAndAddToStock() is pressed.
  // The limit check below is a network round-trip, so it runs BEFORE the
  // add — a scan that would go over the limit never makes it into the draft
  // at all (capped at the allowed remaining quantity), rather than being
  // added and then un-added.
  const addOne = useCallback(
    async (raw: string) => {
      const pn = extractPartNumber(raw);
      if (!pn || busy.current) return;
      busy.current = true;
      const draftQty = counts.find((c) => c.pn === pn)?.qty || 0;
      const allowed = await checkUnitAgainstLimit(pn, draftQty);
      if (!allowed) {
        setTimeout(() => (busy.current = false), 300);
        return;
      }
      triggerScanFeedback();
      setCounts((prev) => {
        const i = prev.findIndex((c) => c.pn === pn);
        if (i >= 0) { const cp = [...prev]; cp[i] = { ...cp[i], qty: cp[i].qty + 1 }; return cp; }
        return [{ pn, qty: 1 }, ...prev];
      });
      setTimeout(() => (busy.current = false), 300);
    },
    [triggerScanFeedback, counts, checkUnitAgainstLimit],
  );

  const incQty = useCallback(
    async (pn: string) => {
      // The +1 stepper is just as capable of pushing a line over its limit as
      // another scan would be — same check, so it can't be used to bypass it.
      const draftQty = counts.find((c) => c.pn === pn)?.qty || 0;
      const allowed = await checkUnitAgainstLimit(pn, draftQty);
      if (!allowed) return;
      setCounts((prev) => prev.map((c) => (c.pn === pn ? { ...c, qty: c.qty + 1 } : c)));
    },
    [counts, checkUnitAgainstLimit],
  );
  const decQty = useCallback((pn: string) => {
    setCounts((prev) => prev.map((c) => (c.pn === pn ? { ...c, qty: Math.max(1, c.qty - 1) } : c)));
  }, []);
  const removeLine = useCallback((pn: string) => {
    setCounts((prev) => prev.filter((c) => c.pn !== pn));
  }, []);

  // The actual write: one /buy call per unit (the endpoint has no quantity field —
  // each call inserts exactly one stock unit), run sequentially per line. A line
  // that partially fails (e.g. a purchase limit hit mid-way) keeps only the
  // still-unadded remainder in the draft, so re-confirming later can't double-add
  // units that already made it into stock.
  const confirmAndAddToStock = useCallback(async () => {
    if (!counts.length || confirming) return;
    setConfirming(true);
    const remaining: { pn: string; qty: number }[] = [];
    const issues: string[] = [];
    let added = 0;
    for (const c of counts) {
      let ok = 0;
      let stopReason = "";
      for (let i = 0; i < c.qty; i++) {
        try {
          await api.post("/buy", {
            part_number: c.pn,
            company,
            condition: "Unknown",
            location: { gps },
            override: false,
          });
          ok++;
        } catch (e: any) {
          stopReason = e?.detail?.code === "LIMIT_REACHED" ? "limit reached" : (e?.detail?.message || e?.message || "failed");
          break;
        }
      }
      added += ok;
      const left = c.qty - ok;
      if (left > 0) {
        remaining.push({ pn: c.pn, qty: left });
        issues.push(`${c.pn}: added ${ok}/${c.qty}${stopReason ? ` — ${stopReason}` : ""}`);
      }
    }
    setCounts(remaining);
    setConfirming(false);
    if (issues.length) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      show(`Added ${added} unit(s) — ${issues.length} line(s) still need attention`, added ? "info" : "error");
    } else {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      show(`Added ${added} unit(s) to stock`, "success");
      router.replace("/(tabs)/inventory" as any);
    }
  }, [counts, confirming, company, gps, show, router]);

  useEffect(() => {
    let cancelled = false;
    const hints = new Map<DecodeHintType, any>();
    hints.set(DecodeHintType.TRY_HARDER, true);
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [
      BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
      BarcodeFormat.EAN_13, BarcodeFormat.UPC_A, BarcodeFormat.ITF, BarcodeFormat.PDF_417,
    ]);
    const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 25 });
    (async () => {
      let t = 0;
      while (!videoRef.current && t < 40) { await new Promise((r) => setTimeout(r, 50)); t++; }
      if (cancelled || !videoRef.current) return;
      try {
        controlsRef.current = await reader.decodeFromConstraints(
          { video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } } },
          videoRef.current,
          (res) => {
            if (!res) return;
            const code = res.getText();
            const now = Date.now();
            if (code === last.current.c && now - last.current.at < 900) return;
            last.current = { c: code, at: now };
            addOne(code);
          },
        );
      } catch {}
    })();
    return () => { cancelled = true; try { controlsRef.current?.stop?.(); } catch {} };
  }, [addOne]);

  const VideoEl = useMemo(
    () => unstable_createElement("video", { ref: videoRef, autoPlay: true, muted: true, playsInline: true, style: { width: "100%", height: "100%", objectFit: "cover", backgroundColor: "#000" } }),
    [],
  );

  return (
    <View style={[styles.flex, dangerBorder && styles.dangerBorder]} testID="batch-danger-border">
      <Header title="Multiple Buy" subtitle={isSuperAdmin ? (gps ? "📍 GPS ✓" : "GPS…") : undefined} onBack={() => router.back()} />
      {stopMessage ? (
        <View style={styles.stopBanner} testID="batch-stop-banner">
          <Ionicons name="hand-left" size={20} color={colors.onError} />
          <Text style={styles.stopBannerText} numberOfLines={2}>{stopMessage}</Text>
        </View>
      ) : null}
      <View style={styles.cam}>
        {VideoEl}
        <Animated.View style={[styles.flashOverlay, flashStyle]} pointerEvents="none" testID="batch-scan-flash" />
        <Animated.View style={[styles.dangerFlashOverlay, dangerFlashStyle]} pointerEvents="none" testID="batch-danger-flash" />
        <View style={styles.overlay} pointerEvents="none">
          <View style={[styles.bracket, dangerBorder && styles.bracketDanger]} />
          <Text style={styles.hint}>Scan → adds to draft below (+1 qty). Nothing saved until you confirm.</Text>
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
            <View style={styles.qtyControls}>
              <Pressable style={styles.qtyBtn} onPress={() => decQty(item.pn)} testID={`batch-minus-${item.pn}`}>
                <Ionicons name="remove" size={18} color={colors.onSurface} />
              </Pressable>
              <View style={styles.qtyBadge}><Text style={styles.qtyText}>{item.qty}</Text></View>
              <Pressable style={styles.qtyBtn} onPress={() => incQty(item.pn)} testID={`batch-plus-${item.pn}`}>
                <Ionicons name="add" size={18} color={colors.onSurface} />
              </Pressable>
              <Pressable style={styles.delBtn} onPress={() => removeLine(item.pn)} testID={`batch-remove-${item.pn}`}>
                <Ionicons name="trash" size={18} color={colors.error} />
              </Pressable>
            </View>
          </View>
        )}
      />
      <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
        <Button
          title={confirming ? "Adding to stock…" : `Confirm & Add to Stock (${total})`}
          onPress={confirmAndAddToStock}
          loading={confirming}
          disabled={confirming || counts.length === 0}
          icon="checkmark-circle"
          testID="batch-confirm"
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  // Full-screen red border for the duration of a limit-reached warning —
  // visible even if the operator's attention is on the draft list below,
  // not just the camera.
  dangerBorder: { borderWidth: 4, borderColor: colors.error },
  cam: { height: 280, backgroundColor: "#000", overflow: "hidden" },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center", gap: spacing.md },
  // Box +30% (220x120 -> 286x156); border 5x thinner than the prior 30 -> 6.
  bracket: { width: 286, height: 156, borderWidth: 6, borderColor: colors.success, borderRadius: radius.md },
  bracketDanger: { borderColor: colors.error },
  hint: { color: "#fff", fontWeight: "700", fontSize: font.base },
  flashOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.success },
  // Same mechanism/position as flashOverlay above, red instead of green —
  // the "similar to the existing success flash, but red" cue over the camera.
  dangerFlashOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.error },
  stopBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.error,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  stopBannerText: { color: colors.onError, fontWeight: "900", fontSize: font.lg, letterSpacing: 0.5, flex: 1 },
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
  qtyControls: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  qtyBtn: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center" },
  delBtn: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.error, alignItems: "center", justifyContent: "center", marginLeft: spacing.xs },
  qtyBadge: { backgroundColor: colors.success, borderRadius: radius.md, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, minWidth: 48, alignItems: "center" },
  qtyText: { color: colors.onSuccess, fontWeight: "900", fontSize: 22 },
  bar: { position: "absolute", bottom: 0, left: 0, right: 0, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
});
