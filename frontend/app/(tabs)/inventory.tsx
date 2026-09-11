import { useCallback, useRef, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Linking,
  Modal,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import * as Haptics from "expo-haptics";
import { CameraView, useCameraPermissions } from "expo-camera";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { ConfirmModal, Header, StatusChip, Loading, EmptyState, FilterChip, Button } from "@/src/components/ui";
import {
  EMPTY_LOCATION,
  LocationPicker,
  formatAssignedLocation,
  locationToQueryParams,
  type AssignedLocation,
} from "@/src/components/LocationPicker";
import { printInventory, brandingFromUser } from "@/src/utils/print";
import { extractPartNumber } from "@/src/utils/barcode";
import { colors, font, radius, spacing } from "@/src/theme";

type LocationCheckResult = {
  part_number: string;
  assigned_location: AssignedLocation | null;
  current_location: AssignedLocation | null;
  location_mismatch: boolean;
  units_total: number;
  units_with_location: number;
  inconsistent_locations: AssignedLocation[] | null;
};

type Unit = {
  id: string;
  part_number: string;
  condition: string;
  location: Record<string, string>;
  assigned_location?: AssignedLocation | null;
  part_name?: string;
  company?: string;
};

const CONDITIONS = ["All", "Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown"];

export default function Inventory() {
  const router = useRouter();
  const { user } = useAuth();
  const { show } = useToast();
  const isAdmin = user?.role === "admin";
  const isSuperAdmin = user?.role === "super_admin";
  const [units, setUnits] = useState<Unit[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [cond, setCond] = useState("All");
  const [busy, setBusy] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Unit | null>(null);
  // Scan-to-filter: a barcode scan (or typed text) narrows the list to that part number.
  const [pnFilter, setPnFilter] = useState("");
  const [scannerOpen, setScannerOpen] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();
  const scannedRef = useRef(false);
  // Location-check result for the currently scanned/searched part. currentLoc is
  // the structured address of where the staff member is physically standing —
  // compared field-by-field against assigned_location on the backend, not GPS
  // (GPS coords never match a manual rack/shelf label, so that comparison was
  // always going to false-positive).
  const [locCheck, setLocCheck] = useState<LocationCheckResult | null>(null);
  const [checkingLoc, setCheckingLoc] = useState(false);
  const [currentLoc, setCurrentLoc] = useState<AssignedLocation>({
    ...EMPTY_LOCATION,
    store_name: isSuperAdmin ? null : user?.store_name || null,
  });
  const [locPickerOpen, setLocPickerOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (cond !== "All") params.set("condition", cond);
      if (pnFilter.trim()) params.set("q", pnFilter.trim());
      const qs = params.toString();
      const data = await api.get<Unit[]>(`/inventory${qs ? `?${qs}` : ""}`);
      setUnits(data);
    } catch {
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [cond, pnFilter]);

  // Calls GET /inventory/location-check for `pn`, comparing it against the part's
  // assigned_location using the structured address the staff member picked
  // (currentLoc) — a field-by-field comparison, not GPS. Runs on a completed
  // scan or an explicit "Check Location" tap — not on every keystroke.
  const checkLocation = useCallback(
    async (raw: string) => {
      const pn = raw.trim();
      if (!pn) {
        setLocCheck(null);
        return;
      }
      setCheckingLoc(true);
      try {
        const params = new URLSearchParams({ part_number: pn, ...locationToQueryParams(currentLoc) });
        const res = await api.get<LocationCheckResult>(`/inventory/location-check?${params.toString()}`);
        setLocCheck(res);
      } catch {
        setLocCheck(null);
      } finally {
        setCheckingLoc(false);
      }
    },
    [currentLoc],
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
    const pn = extractPartNumber(data);
    setPnFilter(pn);
    setScannerOpen(false);
    show(`Filtering by: ${pn}`, "success");
    checkLocation(pn);
  };

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const adjust = async (pn: string, delta: number) => {
    if (busy) return;
    setBusy(true);
    try {
      await api.post("/stock/adjust", { part_number: pn, delta });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      await load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = (u: Unit) => setPendingDelete(u);

  const performDelete = async () => {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await api.del(`/stock/unit/${pendingDelete.id}`);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      show("Unit deleted", "success");
      setPendingDelete(null);
      await load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setBusy(false);
    }
  };

  const locStr = (l: Record<string, string>) => {
    const parts = [l.rack, l.shelf, l.box, l.position].filter(Boolean);
    return parts.length ? parts.join(" → ") : "No location";
  };

  return (
    <View style={styles.flex}>
      <Header
        title="Inventory"
        subtitle="Physical stock units"
        right={
          units.length ? (
            <Pressable
              onPress={async () => printInventory(await brandingFromUser(user), units)}
              hitSlop={12}
              testID="print-inventory"
            >
              <Ionicons name="print" size={22} color={colors.brand} />
            </Pressable>
          ) : undefined
        }
      />
      <View style={styles.pnRow}>
        <TextInput
          style={[styles.pnInput, { flex: 1 }]}
          value={pnFilter}
          onChangeText={(t) => { setPnFilter(t); setLocCheck(null); }}
          onSubmitEditing={() => checkLocation(pnFilter)}
          returnKeyType="search"
          placeholder="Filter by part number"
          placeholderTextColor={colors.info}
          autoCapitalize="characters"
          autoCorrect={false}
          testID="inv-pn-filter"
        />
        {pnFilter ? (
          <Pressable style={styles.clearBtn} onPress={() => { setPnFilter(""); setLocCheck(null); }} hitSlop={8} testID="inv-pn-clear">
            <Ionicons name="close" size={18} color={colors.info} />
          </Pressable>
        ) : null}
        <Pressable style={styles.scanBtn} onPress={openScanner} testID="inv-scan">
          <Ionicons name="barcode-outline" size={20} color={colors.onBrand} />
          <Text style={styles.scanBtnText}>Scan</Text>
        </Pressable>
      </View>

      <Pressable style={styles.rackRow} onPress={() => setLocPickerOpen((o) => !o)} testID="inv-current-loc-toggle">
        <Ionicons name="location-outline" size={16} color={colors.info} />
        <Text style={styles.rackToggleText} numberOfLines={1}>
          {formatAssignedLocation(currentLoc) || "Current Location (where you're checking from)"}
        </Text>
        <Ionicons name={locPickerOpen ? "chevron-up" : "chevron-down"} size={16} color={colors.info} />
      </Pressable>
      {locPickerOpen ? (
        <View style={styles.rackPickerWrap}>
          <LocationPicker
            value={currentLoc}
            onChange={setCurrentLoc}
            showStoreName={isSuperAdmin}
            testIDPrefix="inv-loc"
          />
          <Button
            title="Check Location"
            onPress={() => checkLocation(locCheck?.part_number || pnFilter)}
            icon="search"
            testID="inv-check-location"
          />
        </View>
      ) : null}

      {checkingLoc ? (
        <View style={styles.locBannerNeutral} testID="loc-checking">
          <ActivityIndicator size="small" color={colors.info} />
          <Text style={styles.locBannerNeutralText}>Checking location…</Text>
        </View>
      ) : locCheck && (locCheck.location_mismatch || (locCheck.inconsistent_locations && locCheck.inconsistent_locations.length > 1)) ? (
        <View style={styles.locBannerBad} testID="loc-warning">
          <Ionicons name="warning" size={22} color="#fff" />
          <Text style={styles.locBannerBadText}>
            {locCheck.location_mismatch
              ? `⚠️ WRONG LOCATION — should be at: ${formatAssignedLocation(locCheck.assigned_location) || "unknown"}`
              : `⚠️ INCONSISTENT LOCATIONS ON RECORD: ${(locCheck.inconsistent_locations || []).map((l) => formatAssignedLocation(l)).join(", ")}`}
          </Text>
        </View>
      ) : locCheck && locCheck.assigned_location ? (
        <View style={styles.locBannerGood} testID="loc-ok">
          <Ionicons name="checkmark-circle" size={22} color="#fff" />
          <Text style={styles.locBannerGoodText}>✅ Correct location: {formatAssignedLocation(locCheck.assigned_location)}</Text>
        </View>
      ) : locCheck ? (
        <View style={styles.locBannerNeutral} testID="loc-none">
          <Ionicons name="information-circle" size={16} color={colors.info} />
          <Text style={styles.locBannerNeutralText}>No assigned location on record for {locCheck.part_number}.</Text>
        </View>
      ) : null}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.chipScroller}
        contentContainerStyle={styles.chipRow}
      >
        {CONDITIONS.map((c) => (
          <FilterChip key={c} label={c} active={cond === c} onPress={() => setCond(c)} testID={`cond-${c}`} />
        ))}
      </ScrollView>

      {loading ? (
        <Loading />
      ) : units.length === 0 ? (
        <EmptyState
          icon="cube-outline"
          title={pnFilter ? "No match" : "No stock"}
          subtitle={pnFilter ? `Nothing found for "${pnFilter}"` : "Add stock from the Buy module"}
        />
      ) : (
        <FlatList
          data={units}
          keyExtractor={(u) => u.id}
          contentContainerStyle={{ padding: spacing.lg, paddingTop: spacing.sm, gap: spacing.md }}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                setRefreshing(true);
                load();
              }}
              tintColor={colors.brand}
            />
          }
          renderItem={({ item }) => (
            <View style={styles.card}>
              <Pressable
                style={styles.row}
                onPress={() => router.push(`/part/${encodeURIComponent(item.part_number)}` as any)}
                testID={`unit-${item.id}`}
              >
                <View style={{ flex: 1 }}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                  {item.part_name ? <Text style={styles.name}>{item.part_name}</Text> : null}
                  <View style={styles.locRow}>
                    <Ionicons name="location" size={13} color={colors.info} />
                    <Text style={styles.loc}>{locStr(item.location || {})}</Text>
                  </View>
                  {item.assigned_location ? (
                    <View style={styles.arrangedRow}>
                      <Ionicons name="pricetag" size={12} color={colors.success} />
                      <Text style={styles.arrangedText}>{formatAssignedLocation(item.assigned_location)}</Text>
                    </View>
                  ) : (
                    <View style={styles.pendingBadge} testID={`pending-${item.id}`}>
                      <Text style={styles.pendingBadgeText}>⏳ Location Pending</Text>
                    </View>
                  )}
                </View>
                <StatusChip status={item.condition} />
              </Pressable>
              {isAdmin ? (
                <View style={styles.adminBar}>
                  <Pressable
                    style={styles.adminBtn}
                    onPress={() => adjust(item.part_number, -1)}
                    testID={`dec-${item.id}`}
                  >
                    <Ionicons name="remove" size={18} color={colors.warning} />
                    <Text style={[styles.adminBtnText, { color: colors.warning }]}>Reduce</Text>
                  </Pressable>
                  <Pressable
                    style={styles.adminBtn}
                    onPress={() => adjust(item.part_number, 1)}
                    testID={`inc-${item.id}`}
                  >
                    <Ionicons name="add" size={18} color={colors.success} />
                    <Text style={[styles.adminBtnText, { color: colors.success }]}>Add</Text>
                  </Pressable>
                  <Pressable style={styles.adminBtn} onPress={() => confirmDelete(item)} testID={`del-${item.id}`}>
                    <Ionicons name="trash" size={16} color={colors.error} />
                    <Text style={[styles.adminBtnText, { color: colors.error }]}>Delete</Text>
                  </Pressable>
                </View>
              ) : null}
            </View>
          )}
        />
      )}

      <ConfirmModal
        visible={!!pendingDelete}
        title="Delete this unit?"
        message={pendingDelete ? `One unit of ${pendingDelete.part_number} will be permanently deleted.` : ""}
        confirmText="Delete"
        danger
        loading={busy}
        onConfirm={performDelete}
        onCancel={() => setPendingDelete(null)}
      />

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
  pnRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingTop: spacing.sm },
  pnInput: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, color: colors.onSurface, fontSize: font.base },
  rackRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    marginHorizontal: spacing.lg,
    marginTop: spacing.sm,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  rackToggleText: { flex: 1, color: colors.onSurface2, fontSize: font.sm, fontWeight: "600" },
  rackPickerWrap: {
    marginHorizontal: spacing.lg,
    marginTop: spacing.sm,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.md,
    gap: spacing.sm,
  },
  clearBtn: { width: 32, height: 32, alignItems: "center", justifyContent: "center" },
  scanBtn: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  scanBtnText: { color: colors.onBrand, fontWeight: "800", fontSize: font.sm },
  scanModal: { flex: 1, backgroundColor: "#000" },
  scanOverlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center" },
  scanBracket: { width: 240, height: 160, borderWidth: 3, borderColor: colors.brand, borderRadius: radius.md },
  scanHint: { color: "#fff", fontSize: font.base, marginTop: spacing.lg, textAlign: "center", paddingHorizontal: spacing.xl },
  scanClose: { position: "absolute", top: 48, right: 20, width: 44, height: 44, borderRadius: 22, backgroundColor: "rgba(0,0,0,0.5)", alignItems: "center", justifyContent: "center" },
  locBannerBad: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.error, marginHorizontal: spacing.lg, marginTop: spacing.sm, borderRadius: radius.md, padding: spacing.md },
  locBannerBadText: { flex: 1, color: "#fff", fontWeight: "900", fontSize: font.base, letterSpacing: 0.3 },
  locBannerGood: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.success, marginHorizontal: spacing.lg, marginTop: spacing.sm, borderRadius: radius.md, padding: spacing.md },
  locBannerGoodText: { flex: 1, color: "#fff", fontWeight: "800", fontSize: font.base },
  locBannerNeutral: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, marginHorizontal: spacing.lg, marginTop: spacing.sm, borderRadius: radius.md, padding: spacing.md },
  locBannerNeutralText: { flex: 1, color: colors.info, fontSize: font.sm, fontWeight: "600" },
  chipScroller: { maxHeight: 56, borderBottomWidth: 1, borderBottomColor: colors.divider },
  chipRow: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, alignItems: "center" },
  card: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, overflow: "hidden" },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.lg,
  },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  locRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: spacing.xs },
  loc: { color: colors.info, fontSize: font.sm },
  arrangedRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 4 },
  arrangedText: { color: colors.success, fontSize: font.sm, fontWeight: "700" },
  pendingBadge: {
    alignSelf: "flex-start",
    backgroundColor: "#3a3300",
    borderWidth: 1,
    borderColor: colors.warning,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    marginTop: 4,
  },
  pendingBadgeText: { color: colors.warning, fontSize: font.sm - 1, fontWeight: "800" },
  adminBar: { flexDirection: "row", borderTopWidth: 1, borderTopColor: colors.divider },
  adminBtn: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 4, paddingVertical: spacing.sm },
  adminBtnText: { fontSize: font.sm, fontWeight: "800" },
});
