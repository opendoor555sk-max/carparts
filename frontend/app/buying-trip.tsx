import { useCallback, useRef, useState } from "react";
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { CameraView, useCameraPermissions } from "expo-camera";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Field, Header, StatusChip } from "@/src/components/ui";
import { extractPartNumber } from "@/src/utils/barcode";
import { colors, font, radius, spacing, statusColor } from "@/src/theme";

type ScanResult = {
  part_number: string;
  status: string;
  stock_count: number;
  buy_status: string;
  limit: any;
};

export default function BuyingTrip() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { can } = useAuth();
  const { show } = useToast();

  const [manual, setManual] = useState("");
  const [results, setResults] = useState<ScanResult[]>([]);
  const [scanning, setScanning] = useState(false);
  const [camOpen, setCamOpen] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();
  const lastScan = useRef("");

  const doScan = useCallback(
    async (pn: string) => {
      const partNumber = extractPartNumber(pn);
      if (!partNumber) return;
      try {
        const res = await api.post<ScanResult>("/buying-trip/scan", { part_number: partNumber });
        setResults((prev) => [res, ...prev.filter((r) => r.part_number !== partNumber)]);
        if (res.buy_status === "DO NOT BUY") {
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
        } else {
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        }
        setManual("");
      } catch (e: any) {
        show(e?.message || "Scan failed", "error");
      }
    },
    [show],
  );

  const onBarcode = useCallback(
    ({ data }: { data: string }) => {
      if (!scanning || data === lastScan.current) return;
      lastScan.current = data;
      setScanning(false);
      doScan(data);
      setTimeout(() => {
        lastScan.current = "";
        setScanning(true);
      }, 1500);
    },
    [scanning, doScan],
  );

  const openCamera = async () => {
    if (Platform.OS === "web") {
      show("Web પર camera limited — manual વાપરો", "info");
      return;
    }
    if (!permission?.granted) {
      const r = await requestPermission();
      if (!r.granted) {
        show("Camera permission જરૂરી", "error");
        return;
      }
    }
    setCamOpen(true);
    setScanning(true);
  };

  return (
    <View style={styles.flex}>
      <Header
        title="Buying Trip"
        subtitle={`${results.length} scanned`}
        onBack={() => router.back()}
        right={
          <View style={styles.syncPill}>
            <Ionicons name="cloud-done" size={14} color={colors.success} />
            <Text style={styles.syncText}>Synced</Text>
          </View>
        }
      />

      {camOpen && Platform.OS !== "web" ? (
        <View style={styles.camWrap}>
          <CameraView
            style={StyleSheet.absoluteFill}
            facing="back"
            barcodeScannerSettings={{ barcodeTypes: ["qr", "ean13", "code128", "code39", "upc_a", "datamatrix"] }}
            onBarcodeScanned={scanning ? onBarcode : undefined}
          />
          <View style={styles.camOverlay}>
            <View style={styles.bracket} />
          </View>
          <Pressable style={styles.closeCam} onPress={() => setCamOpen(false)} testID="close-camera">
            <Ionicons name="close" size={22} color="#fff" />
            <Text style={styles.closeCamText}>Close Camera</Text>
          </Pressable>
        </View>
      ) : null}

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <View style={styles.inputBar}>
          <View style={{ flex: 1 }}>
            <Field
              value={manual}
              onChangeText={setManual}
              placeholder="Part number scan/type"
              autoCapitalize="characters"
              onSubmitEditing={() => doScan(manual)}
              returnKeyType="search"
              testID="trip-input"
            />
          </View>
          <Pressable style={styles.camBtn} onPress={openCamera} testID="trip-camera">
            <Ionicons name="camera" size={22} color={colors.onBrand} />
          </Pressable>
        </View>

        <FlatList
          data={results}
          keyExtractor={(r, i) => `${r.part_number}-${i}`}
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Ionicons name="scan" size={48} color={colors.borderStrong} />
              <Text style={styles.emptyText}>Part scan કરો — stock, limit, requirement અને buy decision તરત મળશે</Text>
            </View>
          }
          renderItem={({ item }) => {
            const c = statusColor(item.buy_status);
            const doNot = item.buy_status === "DO NOT BUY";
            return (
              <View style={[styles.resultCard, { borderColor: c.border, borderWidth: doNot ? 2 : 1 }]} testID={`trip-result-${item.part_number}`}>
                <View style={styles.rowBetween}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                  <StatusChip status={item.status} />
                </View>
                <View style={[styles.buyBanner, { backgroundColor: c.fg }]}>
                  <Text style={[styles.buyBannerText, { color: doNot ? colors.onError : colors.onBrand }]}>
                    {item.buy_status}
                  </Text>
                </View>
                <View style={styles.metaRow}>
                  <Meta label="Stock" value={String(item.stock_count)} />
                  <Meta
                    label="Limit"
                    value={item.limit?.limit_enabled ? String(item.limit.allowed_limit) : "—"}
                  />
                  <Meta
                    label="Remaining"
                    value={item.limit?.remaining !== null && item.limit?.remaining !== undefined ? String(item.limit.remaining) : "—"}
                  />
                </View>
                {can("buy") && !doNot ? (
                  <Button
                    title="Buy this part"
                    onPress={() => router.push(`/buy?pn=${encodeURIComponent(item.part_number)}` as any)}
                    variant="secondary"
                    icon="download"
                    testID={`trip-buy-${item.part_number}`}
                    style={{ marginTop: spacing.sm }}
                  />
                ) : null}
              </View>
            );
          }}
        />
      </KeyboardAvoidingView>
    </View>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.meta}>
      <Text style={styles.metaLabel}>{label}</Text>
      <Text style={styles.metaValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  syncPill: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.pill, paddingHorizontal: spacing.sm, paddingVertical: 4 },
  syncText: { color: colors.onSurface2, fontSize: font.sm - 1, fontWeight: "700" },
  camWrap: { height: 260, backgroundColor: "#000" },
  camOverlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center" },
  bracket: { width: 200, height: 120, borderWidth: 3, borderColor: colors.brand, borderRadius: radius.md },
  closeCam: { position: "absolute", bottom: spacing.md, alignSelf: "center", flexDirection: "row", alignItems: "center", gap: spacing.xs, backgroundColor: "rgba(0,0,0,0.6)", paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, borderRadius: radius.pill },
  closeCamText: { color: "#fff", fontWeight: "700" },
  inputBar: { flexDirection: "row", gap: spacing.sm, padding: spacing.lg, alignItems: "flex-start", borderBottomWidth: 1, borderBottomColor: colors.divider },
  camBtn: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  empty: { alignItems: "center", gap: spacing.md, padding: spacing.xxxl },
  emptyText: { color: colors.info, textAlign: "center", fontSize: font.base, lineHeight: 20 },
  resultCard: { backgroundColor: colors.surface2, borderRadius: radius.md, padding: spacing.lg, gap: spacing.md },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  buyBanner: { borderRadius: radius.sm, paddingVertical: spacing.md, alignItems: "center" },
  buyBannerText: { fontSize: font.lg, fontWeight: "800", letterSpacing: 1 },
  metaRow: { flexDirection: "row", gap: spacing.sm },
  meta: { flex: 1, backgroundColor: colors.surface, borderRadius: radius.sm, padding: spacing.md, alignItems: "center", borderWidth: 1, borderColor: colors.border },
  metaLabel: { color: colors.info, fontSize: font.sm - 1 },
  metaValue: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800" },
});
