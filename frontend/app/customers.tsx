import { useCallback, useRef, useState } from "react";
import { FlatList, Linking, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { CameraView, useCameraPermissions } from "expo-camera";
import { useFocusEffect, useRouter } from "expo-router";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Customer = { id: string; name: string; phone: string; address?: string; balance: number };

// Customer Ledger (Grahak Khata) list. A customer's "ID/card" barcode is
// just their phone number encoded as a QR/barcode — no separate ID scheme —
// so scanning one is exactly a phone lookup (GET /customers?phone=...), the
// fast path to their ledger without typing/searching.
export default function Customers() {
  const router = useRouter();
  const { show } = useToast();
  const [q, setQ] = useState("");
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(true);
  const [scannerOpen, setScannerOpen] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();
  const scannedRef = useRef(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async (query: string) => {
    try {
      const params = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : "";
      setCustomers(await api.get<Customer[]>(`/customers${params}`));
    } catch {
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load(q);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [load]),
  );

  const onChangeQuery = (text: string) => {
    setQ(text);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => load(text), 300);
  };

  const lookupByPhone = useCallback(
    async (phone: string) => {
      try {
        const matches = await api.get<Customer[]>(`/customers?phone=${encodeURIComponent(phone)}`);
        if (matches.length) {
          router.push(`/customer/${matches[0].id}` as any);
        } else {
          show(`No customer found for ${phone}`, "info");
        }
      } catch (e: any) {
        show(e?.message || "Lookup failed", "error");
      }
    },
    [router, show],
  );

  const openScanner = async () => {
    let perm = permission;
    if (!perm?.granted) perm = await requestPermission();
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
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    lookupByPhone(data.trim());
  };

  return (
    <View style={styles.flex}>
      <Header title="Grahak Khata" subtitle="Customer ledger" onBack={() => router.back()} />

      <View style={styles.searchRow}>
        <View style={{ flex: 1 }}>
          <Field
            value={q}
            onChangeText={onChangeQuery}
            placeholder="Search by name or phone"
            keyboardType="default"
            testID="customer-search"
          />
        </View>
        <Pressable style={styles.iconBtn} onPress={openScanner} testID="customer-scan-btn">
          <Ionicons name="scan" size={22} color={colors.onBrand} />
        </Pressable>
        <Pressable style={styles.iconBtn} onPress={() => router.push("/customer-new" as any)} testID="customer-add-btn">
          <Ionicons name="person-add" size={22} color={colors.onBrand} />
        </Pressable>
      </View>

      {loading ? (
        <Loading />
      ) : customers.length === 0 ? (
        <EmptyState
          icon="people-outline"
          title="No customers"
          subtitle={q ? `Nothing found for "${q}"` : "Add a customer to start their ledger"}
          action={<Button title="Add Customer" onPress={() => router.push("/customer-new" as any)} icon="person-add" testID="customer-add-empty" />}
        />
      ) : (
        <FlatList
          data={customers}
          keyExtractor={(c) => c.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm }}
          renderItem={({ item }) => (
            <Pressable
              style={styles.row}
              onPress={() => router.push(`/customer/${item.id}` as any)}
              testID={`customer-${item.id}`}
            >
              <View style={styles.avatar}>
                <Ionicons name="person" size={20} color={colors.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.name}>{item.name}</Text>
                <Text style={styles.phone}>{item.phone}</Text>
              </View>
              <View style={styles.balanceWrap}>
                <Text style={styles.balanceLabel}>{item.balance > 0 ? "OWES" : item.balance < 0 ? "ADVANCE" : "SETTLED"}</Text>
                <Text style={[styles.balance, { color: item.balance > 0 ? colors.error : item.balance < 0 ? colors.success : colors.info }]}>
                  ₹{Math.abs(item.balance).toFixed(2)}
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.info} />
            </Pressable>
          )}
        />
      )}

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
            <Text style={styles.scanHint}>Scan the customer ID/card barcode</Text>
          </View>
          <Pressable style={styles.scanClose} onPress={() => setScannerOpen(false)} testID="customer-scan-close">
            <Ionicons name="close" size={26} color="#fff" />
          </Pressable>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  searchRow: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.divider },
  iconBtn: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.brandFaint, alignItems: "center", justifyContent: "center" },
  name: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  phone: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  balanceWrap: { alignItems: "flex-end" },
  balanceLabel: { color: colors.info, fontSize: font.sm - 2, fontWeight: "700", letterSpacing: 0.5 },
  balance: { fontSize: font.base, fontWeight: "900" },
  scanModal: { flex: 1, backgroundColor: "#000" },
  scanOverlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center" },
  scanBracket: { width: 240, height: 160, borderWidth: 3, borderColor: colors.brand, borderRadius: radius.md },
  scanHint: { color: "#fff", fontSize: font.base, marginTop: spacing.lg, textAlign: "center", paddingHorizontal: spacing.xl },
  scanClose: { position: "absolute", top: 48, right: 20, width: 44, height: 44, borderRadius: 22, backgroundColor: "rgba(0,0,0,0.5)", alignItems: "center", justifyContent: "center" },
});
