import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { unstable_createElement } from "react-native-web";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BrowserMultiFormatReader } from "@zxing/browser";
import { BarcodeFormat, DecodeHintType } from "@zxing/library";
import * as Location from "expo-location";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Field, Header } from "@/src/components/ui";
import { extractPartNumber } from "@/src/utils/barcode";
import { colors, font, radius, spacing } from "@/src/theme";

export default function BatchBuyWeb() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { company = "All" } = useLocalSearchParams<{ company: string }>();
  const { show } = useToast();
  const [manual, setManual] = useState("");
  const [counts, setCounts] = useState<{ pn: string; qty: number }[]>([]);
  const [total, setTotal] = useState(0);
  const [gps, setGps] = useState("");
  const videoRef = useRef<any>(null);
  const controlsRef = useRef<any>(null);
  const busy = useRef(false);
  const last = useRef<{ c: string; at: number }>({ c: "", at: 0 });

  useEffect(() => {
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== "granted") return;
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        setGps(`${loc.coords.latitude.toFixed(5)}, ${loc.coords.longitude.toFixed(5)}`);
      } catch {}
    })();
  }, []);

  const addOne = useCallback(
    async (raw: string) => {
      const pn = extractPartNumber(raw);
      if (!pn || busy.current) return;
      busy.current = true;
      try {
        await api.post("/buy", { part_number: pn, company, condition: "Unknown", location: { gps }, override: false });
        setCounts((prev) => {
          const i = prev.findIndex((c) => c.pn === pn);
          if (i >= 0) { const cp = [...prev]; cp[i] = { ...cp[i], qty: cp[i].qty + 1 }; return cp; }
          return [{ pn, qty: 1 }, ...prev];
        });
        setTotal((t) => t + 1);
      } catch (e: any) {
        if (e?.detail?.code === "LIMIT_REACHED") {
          show(`🚫 ${pn} — Limit પૂરી, DO NOT BUY`, "error");
        } else {
          show(e?.detail?.message || e?.message || "Add failed", "error");
        }
      } finally {
        setTimeout(() => (busy.current = false), 300);
      }
    },
    [company, gps, show],
  );

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
    <View style={styles.flex}>
      <Header title="Multiple Buy" subtitle={`Total: ${total}  •  ${gps ? "📍 GPS ✓" : "GPS…"}`} onBack={() => router.back()} />
      <View style={styles.cam}>
        {VideoEl}
        <View style={styles.overlay} pointerEvents="none">
          <View style={styles.bracket} />
          <Text style={styles.hint}>Scan કરો → દરેક scan = +1 qty</Text>
        </View>
      </View>
      <View style={styles.inputRow}>
        <View style={{ flex: 1 }}>
          <Field value={manual} onChangeText={setManual} placeholder="Manual part no. +1" autoCapitalize="characters" onSubmitEditing={() => { addOne(manual); setManual(""); }} returnKeyType="done" testID="batch-manual" />
        </View>
        <Pressable style={styles.addBtn} onPress={() => { addOne(manual); setManual(""); }} testID="batch-add"><Ionicons name="add" size={24} color={colors.onBrand} /></Pressable>
      </View>
      <FlatList
        data={counts}
        keyExtractor={(c) => c.pn}
        contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: insets.bottom + 90 }}
        ListEmptyComponent={<Text style={styles.empty}>હજી કંઈ scan નથી થયું</Text>}
        renderItem={({ item }) => (
          <View style={styles.row} testID={`batch-${item.pn}`}>
            <Text style={styles.pn}>{item.pn}</Text>
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
  cam: { height: 280, backgroundColor: "#000", overflow: "hidden" },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center", gap: spacing.md },
  bracket: { width: 220, height: 120, borderWidth: 3, borderColor: colors.success, borderRadius: radius.md },
  hint: { color: "#fff", fontWeight: "700", fontSize: font.base },
  inputRow: { flexDirection: "row", gap: spacing.sm, padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.divider },
  addBtn: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  empty: { color: colors.info, textAlign: "center", marginTop: spacing.xl },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  qtyBadge: { backgroundColor: colors.success, borderRadius: radius.pill, paddingHorizontal: spacing.md, paddingVertical: 4 },
  qtyText: { color: colors.onSuccess, fontWeight: "800", fontSize: font.base },
  bar: { position: "absolute", bottom: 0, left: 0, right: 0, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
});
