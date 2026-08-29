import { useCallback, useEffect, useRef, useState } from "react";
import {
  Linking,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { CameraView, useCameraPermissions } from "expo-camera";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";
import * as Location from "expo-location";

import { Button, Field, Header } from "@/src/components/ui";
import { extractPartNumber } from "@/src/utils/barcode";
import { colors, font, radius, spacing } from "@/src/theme";
import { useAuth } from "@/src/context/AuthContext";

const MODE_META: Record<string, { title: string; color: string; verb: string }> = {
  search: { title: "SEARCH", color: colors.info, verb: "Search" },
  buy: { title: "BUY", color: colors.success, verb: "Buy" },
  sell: { title: "SELL", color: colors.brand, verb: "Sell" },
  requirement: { title: "REQUIREMENT", color: colors.warning, verb: "Requirement" },
};

export default function Scan() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const isSuperAdmin = user?.role === "super_admin";
  const { mode = "search", company = "All" } = useLocalSearchParams<{ mode: string; company: string }>();
  const meta = MODE_META[mode as string] || MODE_META.search;

  const [permission, requestPermission] = useCameraPermissions();
  const [manual, setManual] = useState("");
  const [scanning, setScanning] = useState(true);
  const [gps, setGps] = useState("");
  const lastScan = useRef<string>("");

  // Capture GPS location on entry so it is attached to this action (admin can track).
  useEffect(() => {
    (async () => {
      try {
        const perm = await Location.getForegroundPermissionsAsync();
        let granted = perm.granted;
        if (!granted && perm.canAskAgain) granted = (await Location.requestForegroundPermissionsAsync()).granted;
        if (granted) {
          const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
          setGps(`${loc.coords.latitude.toFixed(6)},${loc.coords.longitude.toFixed(6)}`);
        }
      } catch {
        // location optional
      }
    })();
  }, []);

  // Auto-request camera permission on mount so the scanner opens immediately.
  useEffect(() => {
    if (permission && !permission.granted && permission.canAskAgain) {
      requestPermission();
    }
  }, [permission?.granted, permission?.canAskAgain]);

  const proceed = useCallback(
    (partNumber: string) => {
      const pn = extractPartNumber(partNumber);
      if (!pn) return;
      const c = encodeURIComponent(company as string);
      switch (mode) {
        case "buy":
          router.replace(`/buy?pn=${encodeURIComponent(pn)}&company=${c}` as any);
          break;
        case "sell":
          router.replace(`/sell?pn=${encodeURIComponent(pn)}` as any);
          break;
        case "requirement":
          router.replace(`/requirement-new?pn=${encodeURIComponent(pn)}&company=${c}&gps=${encodeURIComponent(gps)}` as any);
          break;
        default:
          router.replace(`/part/${encodeURIComponent(pn)}` as any);
      }
    },
    [mode, company, router],
  );

  const onBarcode = useCallback(
    ({ data }: { data: string }) => {
      if (!scanning || !data || data === lastScan.current) return;
      lastScan.current = data;
      setScanning(false);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      proceed(data);
    },
    [scanning, proceed],
  );

  const renderCameraArea = () => {
    // Permission object still loading
    if (!permission) {
      return (
        <View style={styles.cameraFallback}>
          <Ionicons name="camera-outline" size={48} color={colors.info} />
          <Text style={styles.permSub}>Camera is getting ready…</Text>
        </View>
      );
    }
    // Denied / blocked
    if (!permission.granted) {
      return (
        <View style={styles.cameraFallback}>
          <Ionicons name="camera-outline" size={48} color={colors.brand} />
          <Text style={styles.permTitle}>Camera is needed to scan Barcode / QR</Text>
          <Text style={styles.permSub}>Allow camera to automatically capture the part number</Text>
          {permission.canAskAgain ? (
            <Button title="Allow Camera" onPress={requestPermission} icon="camera" testID="grant-camera" />
          ) : (
            <Button
              title="Open Settings"
              onPress={() => Linking.openSettings()}
              variant="secondary"
              icon="settings"
              testID="open-settings"
            />
          )}
          <Text style={styles.orText}>or use manual entry below</Text>
        </View>
      );
    }
    // Granted -> live camera scanner (native + web)
    return (
      <View style={styles.cameraWrap}>
        <CameraView
          style={StyleSheet.absoluteFill}
          facing="back"
          barcodeScannerSettings={{
            barcodeTypes: ["qr", "ean13", "ean8", "code128", "code39", "code93", "upc_a", "upc_e", "codabar", "itf14", "datamatrix", "pdf417", "aztec"],
          }}
          onBarcodeScanned={scanning ? onBarcode : undefined}
        />
        <View style={styles.overlay} pointerEvents="none">
          <View style={[styles.bracket, { borderColor: meta.color }]} />
          <Text style={styles.scanHint}>Hold the part number barcode/QR in front of the camera</Text>
        </View>
      </View>
    );
  };

  return (
    <View style={styles.flex}>
      <Header
        title={`${meta.title} — Scan`}
        subtitle={`Company: ${company}`}
        onBack={() => router.back()}
      />
      {isSuperAdmin ? (
        <View style={styles.gpsBar} testID="scan-gps">
          <Ionicons name="location" size={14} color={gps ? colors.success : colors.info} />
          <Text style={styles.gpsText}>{gps ? `GPS: ${gps}` : "Getting GPS location…"}</Text>
        </View>
      ) : null}
      <View style={{ flex: 1 }}>
        {renderCameraArea()}

        <View style={[styles.bottom, { paddingBottom: insets.bottom + spacing.lg }]}>
          <Text style={styles.manualLabel}>MANUAL PART NUMBER</Text>
          <View style={styles.manualRow}>
            <View style={{ flex: 1 }}>
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
            </View>
          </View>
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
  cameraWrap: { flex: 1, minHeight: 300, backgroundColor: "#000" },
  cameraFallback: {
    flex: 1,
    minHeight: 300,
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.md,
    padding: spacing.xl,
  },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center", gap: spacing.xl },
  bracket: { width: 240, height: 160, borderWidth: 3, borderRadius: radius.md, backgroundColor: "transparent" },
  scanHint: { color: "#fff", fontSize: font.base, fontWeight: "700", textAlign: "center", paddingHorizontal: spacing.xl },
  permTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", textAlign: "center" },
  permSub: { color: colors.info, fontSize: font.base, textAlign: "center", marginBottom: spacing.sm },
  orText: { color: colors.info, fontSize: font.sm, marginTop: spacing.md },
  gpsBar: { flexDirection: "row", alignItems: "center", gap: spacing.xs, backgroundColor: colors.surface2, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.divider },
  gpsText: { color: colors.onSurface3, fontSize: font.sm, fontWeight: "700" },
  bottom: {
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    padding: spacing.lg,
    gap: spacing.xs,
  },
  manualLabel: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.xs },
  manualRow: { flexDirection: "row", gap: spacing.sm },
});
