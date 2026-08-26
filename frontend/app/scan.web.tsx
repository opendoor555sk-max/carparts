import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Linking, StyleSheet, Text, View } from "react-native";
// react-native-web exposes unstable_createElement to render raw DOM nodes (e.g. <video>).
import { unstable_createElement } from "react-native-web";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BrowserMultiFormatReader } from "@zxing/browser";
import { BarcodeFormat, DecodeHintType } from "@zxing/library";

import { Button, Field, Header } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

const MODE_META: Record<string, { title: string; color: string }> = {
  search: { title: "SEARCH", color: colors.info },
  buy: { title: "BUY", color: colors.success },
  sell: { title: "SELL", color: colors.brand },
  requirement: { title: "REQUIREMENT", color: colors.warning },
};

export default function ScanWeb() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { mode = "search", company = "All" } = useLocalSearchParams<{ mode: string; company: string }>();
  const meta = MODE_META[mode as string] || MODE_META.search;

  const [manual, setManual] = useState("");
  const [status, setStatus] = useState<"init" | "ready" | "denied" | "error">("init");
  const videoRef = useRef<any>(null);
  const readerRef = useRef<BrowserMultiFormatReader | null>(null);
  const controlsRef = useRef<any>(null);
  const doneRef = useRef(false);

  const proceed = useCallback(
    (partNumber: string) => {
      const pn = partNumber.trim();
      if (!pn || doneRef.current) return;
      doneRef.current = true;
      try {
        controlsRef.current?.stop?.();
      } catch {}
      const c = encodeURIComponent(company as string);
      switch (mode) {
        case "buy":
          router.replace(`/buy?pn=${encodeURIComponent(pn)}&company=${c}` as any);
          break;
        case "sell":
          router.replace(`/sell?pn=${encodeURIComponent(pn)}` as any);
          break;
        case "requirement":
          router.replace(`/requirement-new?pn=${encodeURIComponent(pn)}&company=${c}` as any);
          break;
        default:
          router.replace(`/part/${encodeURIComponent(pn)}` as any);
      }
    },
    [mode, company, router],
  );

  useEffect(() => {
    let cancelled = false;
    // Aggressive hints: try hard + all common salvage-label formats (QR + DataMatrix + 1D).
    const hints = new Map<DecodeHintType, any>();
    hints.set(DecodeHintType.TRY_HARDER, true);
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [
      BarcodeFormat.QR_CODE,
      BarcodeFormat.DATA_MATRIX,
      BarcodeFormat.AZTEC,
      BarcodeFormat.PDF_417,
      BarcodeFormat.CODE_128,
      BarcodeFormat.CODE_39,
      BarcodeFormat.CODE_93,
      BarcodeFormat.CODABAR,
      BarcodeFormat.EAN_13,
      BarcodeFormat.EAN_8,
      BarcodeFormat.UPC_A,
      BarcodeFormat.UPC_E,
      BarcodeFormat.ITF,
    ]);
    const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 50 });
    readerRef.current = reader;

    (async () => {
      // Wait for the video DOM node to mount.
      let tries = 0;
      while (!videoRef.current && tries < 40) {
        await new Promise((r) => setTimeout(r, 50));
        tries++;
      }
      if (cancelled || !videoRef.current) return;
      try {
        const controls = await reader.decodeFromConstraints(
          {
            video: {
              facingMode: { ideal: "environment" },
              width: { ideal: 1280 },
              height: { ideal: 720 },
              frameRate: { ideal: 30 },
            },
          },
          videoRef.current,
          (result) => {
            if (result) proceed(result.getText());
          },
        );
        controlsRef.current = controls;
        // Try to enable continuous autofocus so blurry labels come into focus.
        try {
          const stream: MediaStream | null = videoRef.current?.srcObject || null;
          const track = stream?.getVideoTracks?.()[0];
          const caps: any = track?.getCapabilities?.() || {};
          const advanced: any[] = [];
          if (caps.focusMode && caps.focusMode.includes?.("continuous")) advanced.push({ focusMode: "continuous" });
          if (advanced.length && track) await track.applyConstraints({ advanced });
        } catch {}
        if (!cancelled) setStatus("ready");
      } catch (e: any) {
        const name = e?.name || "";
        if (name === "NotAllowedError" || name === "PermissionDeniedError") setStatus("denied");
        else setStatus("error");
      }
    })();

    return () => {
      cancelled = true;
      try {
        controlsRef.current?.stop?.();
      } catch {}
    };
  }, [proceed]);

  // Create the raw <video> DOM node ONCE so ZXing keeps its stream across re-renders.
  const VideoEl = useMemo(
    () =>
      unstable_createElement("video", {
        ref: videoRef,
        autoPlay: true,
        muted: true,
        playsInline: true,
        style: { width: "100%", height: "100%", objectFit: "cover", backgroundColor: "#000" },
      }),
    [],
  );

  return (
    <View style={styles.flex}>
      <Header title={`${meta.title} — Scan`} subtitle={`Company: ${company}`} onBack={() => router.back()} />
      <View style={{ flex: 1 }}>
        <View style={styles.cameraWrap}>
          {VideoEl}
          <View style={[styles.overlay, { pointerEvents: "none" }]}>
            <View style={[styles.bracket, { borderColor: meta.color }]} />
            <Text style={styles.scanHint}>
              {status === "denied"
                ? "Camera permission આપો (browser lock icon → Camera → Allow)"
                : status === "error"
                  ? "આ browser માં camera scan support નથી — manual entry વાપરો"
                  : "Barcode/QR ને bracket માં clear રાખો — auto scan થશે"}
            </Text>
          </View>
        </View>

        {status === "denied" ? (
          <View style={styles.permBar}>
            <Ionicons name="lock-closed" size={16} color={colors.warning} />
            <Text style={styles.permText}>Camera blocked — browser settings માંથી allow કરો</Text>
            <Button title="Settings" onPress={() => Linking.openURL("app-settings:")} variant="ghost" testID="web-open-settings" />
          </View>
        ) : null}

        <View style={[styles.bottom, { paddingBottom: insets.bottom + spacing.lg }]}>
          <Text style={styles.manualLabel}>MANUAL PART NUMBER</Text>
          <Field
            value={manual}
            onChangeText={setManual}
            placeholder="e.g. 39100-2B000"
            autoCapitalize="characters"
            autoCorrect={false}
            onSubmitEditing={() => proceed(manual)}
            returnKeyType="go"
            testID="manual-part-input"
          />
          <Button
            title={`${meta.title} — Continue`}
            onPress={() => proceed(manual)}
            icon="arrow-forward"
            disabled={!manual.trim()}
            testID="scan-continue"
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cameraWrap: { flex: 1, minHeight: 300, backgroundColor: "#000", overflow: "hidden" },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center", gap: spacing.xl },
  bracket: { width: 240, height: 160, borderWidth: 3, borderRadius: radius.md, backgroundColor: "transparent" },
  scanHint: { color: "#fff", fontSize: font.base, fontWeight: "700", textAlign: "center", paddingHorizontal: spacing.xl },
  permBar: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, padding: spacing.md, borderTopWidth: 1, borderTopColor: colors.border },
  permText: { color: colors.onSurface2, fontSize: font.sm, flex: 1 },
  bottom: { backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.lg, gap: spacing.xs },
  manualLabel: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.xs },
});
