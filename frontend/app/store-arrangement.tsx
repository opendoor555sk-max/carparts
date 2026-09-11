import { useCallback, useMemo, useRef, useState } from "react";
import { Linking, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";
import { CameraView, useCameraPermissions } from "expo-camera";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, EmptyState, Header, Loading } from "@/src/components/ui";
import { EMPTY_LOCATION, LocationPicker, isLocationEmpty, type AssignedLocation } from "@/src/components/LocationPicker";
import { extractPartNumber } from "@/src/utils/barcode";
import { brandingFromUser, printLocationSticker } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

type Unit = { id: string; assigned_location?: AssignedLocation | null };
type PartInfo = { part_number: string; name?: string; company?: string; stock_count: number; units: Unit[] };

// Post-buy physical placement: scan/type a part -> pick its shelf/rack address
// -> save it on one stock unit at a time, tracking a running "confirmed this
// session" count against the part's total stock so a short/extra count is
// obvious before staff move on to the next part.
export default function StoreArrangement() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { show } = useToast();
  const isSuperAdmin = user?.role === "super_admin";

  const [manual, setManual] = useState("");
  const [part, setPart] = useState<PartInfo | null>(null);
  const [loadingPart, setLoadingPart] = useState(false);
  const [saving, setSaving] = useState(false);
  // Location carries over between parts on purpose — staff usually arrange a
  // whole run of different parts onto the same rack/shelf in one pass.
  const [loc, setLoc] = useState<AssignedLocation>({
    ...EMPTY_LOCATION,
    store_name: isSuperAdmin ? null : user?.store_name || null,
  });
  // Session-only: how many "Save Location" confirmations happened per part
  // number since this screen was opened — resets on unmount, by design.
  const [sessionConfirmed, setSessionConfirmed] = useState<Record<string, number>>({});
  const [scannerOpen, setScannerOpen] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();
  const scannedRef = useRef(false);

  const loadPart = useCallback(
    async (raw: string) => {
      const pn = extractPartNumber(raw);
      if (!pn) return;
      setLoadingPart(true);
      try {
        const res = await api.get<PartInfo>(`/parts/${encodeURIComponent(pn)}`);
        setPart(res);
      } catch (e: any) {
        setPart(null);
        show(e?.message || `Part "${pn}" not found`, "error");
      } finally {
        setLoadingPart(false);
      }
    },
    [show],
  );

  const openScanner = async () => {
    let perm = permission;
    if (!perm?.granted) {
      perm = await requestPermission();
    }
    if (perm?.granted) {
      scannedRef.current = false;
      setScannerOpen(true);
    } else if (perm && !perm.canAskAgain) {
      show("Camera blocked — enable it in Settings", "error");
      Linking.openSettings();
    } else {
      show("Camera permission needed to scan", "error");
    }
  };

  const onScanned = ({ data }: { data: string }) => {
    if (scannedRef.current || !data) return;
    scannedRef.current = true;
    setScannerOpen(false);
    setManual("");
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    loadPart(data);
  };

  // No per-unit barcode exists — every physical unit of a part shares the same
  // code — so each save targets the next unit that has no location yet, or
  // (once all are arranged) the most recently added one, so re-saving to
  // correct a mistake still works.
  const targetUnit = useMemo(() => {
    if (!part || !part.units.length) return null;
    return part.units.find((u) => !u.assigned_location) || part.units[part.units.length - 1];
  }, [part]);

  const arrangedCount = part ? part.units.filter((u) => !!u.assigned_location).length : 0;
  const expected = part?.stock_count ?? 0;
  const confirmed = part ? sessionConfirmed[part.part_number] || 0 : 0;
  const mismatch = !!part && expected > 0 && confirmed !== expected;

  const saveLocation = useCallback(async () => {
    if (!part || !targetUnit) return;
    if (isLocationEmpty(loc)) {
      show("Pick at least one location field", "error");
      return;
    }
    setSaving(true);
    try {
      await api.patch(`/stock/unit/${targetUnit.id}`, { assigned_location: loc });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      setSessionConfirmed((prev) => ({ ...prev, [part.part_number]: (prev[part.part_number] || 0) + 1 }));
      show("Location saved", "success");
      const fresh = await api.get<PartInfo>(`/parts/${encodeURIComponent(part.part_number)}`);
      setPart(fresh);
    } catch (e: any) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      show(e?.message || "Failed to save location", "error");
    } finally {
      setSaving(false);
    }
  }, [part, targetUnit, loc, show]);

  const printSticker = useCallback(async () => {
    if (!part) return;
    try {
      const b = await brandingFromUser(user);
      await printLocationSticker(b, part.part_number, loc, part.name);
    } catch (e: any) {
      show(e?.message || "Print failed", "error");
    }
  }, [part, loc, user, show]);

  return (
    <View style={styles.flex}>
      <Header title="Store Arrangement" subtitle="Place bought stock physically" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: insets.bottom + spacing.xxxl }}>
        <View style={styles.pnRow}>
          <TextInput
            style={[styles.input, { flex: 1 }]}
            value={manual}
            onChangeText={setManual}
            onSubmitEditing={() => loadPart(manual)}
            returnKeyType="search"
            placeholder="Scan or type part number"
            placeholderTextColor={colors.info}
            autoCapitalize="characters"
            autoCorrect={false}
            testID="arrange-pn-input"
          />
          <Pressable style={styles.scanBtn} onPress={openScanner} testID="arrange-scan">
            <Ionicons name="barcode-outline" size={20} color={colors.onBrand} />
            <Text style={styles.scanBtnText}>Scan</Text>
          </Pressable>
        </View>

        {loadingPart ? (
          <Loading text="Looking up part…" />
        ) : part ? (
          <>
            <Card testID="arrange-part-card">
              <Text style={styles.pnTitle}>{part.part_number}</Text>
              {part.name ? <Text style={styles.partName}>{part.name}</Text> : null}
              <View style={styles.statRow}>
                <View style={styles.statBox}>
                  <Text style={styles.statNum}>{expected}</Text>
                  <Text style={styles.statLbl}>Expected</Text>
                </View>
                <View style={styles.statBox}>
                  <Text style={[styles.statNum, { color: colors.success }]}>{arrangedCount}</Text>
                  <Text style={styles.statLbl}>Arranged</Text>
                </View>
                <View style={styles.statBox}>
                  <Text style={[styles.statNum, { color: mismatch ? colors.warning : colors.success }]}>{confirmed}</Text>
                  <Text style={styles.statLbl}>Confirmed (session)</Text>
                </View>
              </View>

              {expected === 0 ? (
                <View style={styles.warnBanner} testID="arrange-no-stock">
                  <Ionicons name="alert-circle" size={16} color={colors.onWarning} />
                  <Text style={styles.warnBannerText}>No stock on file for this part.</Text>
                </View>
              ) : mismatch ? (
                <View style={styles.warnBanner} testID="arrange-mismatch">
                  <Ionicons name="warning" size={16} color={colors.onWarning} />
                  <Text style={styles.warnBannerText}>
                    {confirmed < expected
                      ? `⚠️ Short by ${expected - confirmed} unit(s) this session`
                      : `⚠️ ${confirmed - expected} extra confirmation(s) this session`}
                  </Text>
                </View>
              ) : (
                <View style={styles.okBanner} testID="arrange-matched">
                  <Ionicons name="checkmark-circle" size={16} color="#fff" />
                  <Text style={styles.okBannerText}>All {expected} unit(s) confirmed this session</Text>
                </View>
              )}
            </Card>

            <LocationPicker
              value={loc}
              onChange={setLoc}
              showStoreName={isSuperAdmin}
              label="Assign Location"
              testIDPrefix="arrange-loc"
            />

            <Button
              title={saving ? "Saving…" : "Save Location"}
              onPress={saveLocation}
              loading={saving}
              disabled={saving || !targetUnit}
              icon="save"
              testID="arrange-save"
            />
            <Button
              title="Print Location Sticker"
              onPress={printSticker}
              variant="secondary"
              icon="print"
              disabled={isLocationEmpty(loc)}
              testID="arrange-print"
            />
          </>
        ) : (
          <EmptyState
            icon="cube-outline"
            title="Scan a part to begin"
            subtitle="Look up a part you just bought, then set its shelf/rack location"
          />
        )}
      </ScrollView>

      <Modal visible={scannerOpen} animationType="slide" onRequestClose={() => setScannerOpen(false)}>
        <View style={styles.scanModal}>
          <CameraView
            style={StyleSheet.absoluteFill}
            facing="back"
            barcodeScannerSettings={{
              barcodeTypes: ["qr", "ean13", "ean8", "code128", "code39", "code93", "upc_a", "upc_e", "codabar", "itf14", "datamatrix", "pdf417", "aztec"],
            }}
            onBarcodeScanned={onScanned}
          />
          <View style={styles.scanOverlay} pointerEvents="none">
            <View style={styles.scanBracket} />
            <Text style={styles.scanHint}>Point the camera at any Barcode or QR code</Text>
          </View>
          <Pressable style={styles.scanClose} onPress={() => setScannerOpen(false)} testID="scan-close">
            <Ionicons name="close" size={26} color="#fff" />
          </Pressable>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  pnRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  input: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.md, color: colors.onSurface, fontSize: font.base },
  scanBtn: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.md },
  scanBtnText: { color: colors.onBrand, fontWeight: "800", fontSize: font.sm },
  pnTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "900", letterSpacing: 0.5 },
  partName: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  statRow: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.md },
  statBox: { flex: 1, alignItems: "center", backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingVertical: spacing.md },
  statNum: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800" },
  statLbl: { color: colors.info, fontSize: font.sm - 1, marginTop: 2, textAlign: "center", paddingHorizontal: 4 },
  warnBanner: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: "#3a3300", borderWidth: 1, borderColor: colors.warning, borderRadius: radius.md, padding: spacing.md, marginTop: spacing.md },
  warnBannerText: { flex: 1, color: colors.warning, fontWeight: "700", fontSize: font.sm },
  okBanner: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.success, borderRadius: radius.md, padding: spacing.md, marginTop: spacing.md },
  okBannerText: { flex: 1, color: "#fff", fontWeight: "700", fontSize: font.sm },
  scanModal: { flex: 1, backgroundColor: "#000" },
  scanOverlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center" },
  scanBracket: { width: 240, height: 160, borderWidth: 3, borderColor: colors.brand, borderRadius: radius.md },
  scanHint: { color: "#fff", fontSize: font.base, marginTop: spacing.lg, textAlign: "center", paddingHorizontal: spacing.xl },
  scanClose: { position: "absolute", top: 48, right: 20, width: 44, height: 44, borderRadius: 22, backgroundColor: "rgba(0,0,0,0.5)", alignItems: "center", justifyContent: "center" },
});
