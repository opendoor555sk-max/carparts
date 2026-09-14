import { useCallback, useRef, useState } from "react";
import { FlatList, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, EmptyState, Field, FilterChip, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type RecordType = "customer_return" | "damaged_stock" | "vendor_return";

type ReturnRecord = {
  id: string;
  type: RecordType;
  part_number: string;
  unit_id?: string | null;
  condition?: string | null;
  note?: string | null;
  customer_id?: string | null;
  vendor_id?: string | null;
  by: string;
  at: string;
};

type PickItem = { id: string; name: string; phone: string };

const TYPE_META: Record<RecordType, { label: string; icon: keyof typeof Ionicons.glyphMap; color: string }> = {
  customer_return: { label: "Customer Return", icon: "arrow-undo", color: colors.brand },
  damaged_stock: { label: "Damaged Stock", icon: "warning", color: colors.error },
  vendor_return: { label: "Vendor Return", icon: "return-up-back", color: colors.info },
};

// Lightweight debounced name/phone search shared by the customer and vendor
// pickers below — same search-as-you-type shape as customers.tsx/vendors.tsx,
// just inline and optional (picking a match is never required to submit).
function usePicker(endpoint: "/customers" | "/vendors") {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PickItem[]>([]);
  const [selected, setSelected] = useState<PickItem | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const search = useCallback(
    (text: string) => {
      setQuery(text);
      setSelected(null);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      if (!text.trim()) {
        setResults([]);
        return;
      }
      debounceRef.current = setTimeout(async () => {
        try {
          setResults(await api.get<PickItem[]>(`${endpoint}?q=${encodeURIComponent(text.trim())}`));
        } catch {
          setResults([]);
        }
      }, 300);
    },
    [endpoint],
  );
  const pick = (item: PickItem) => {
    setSelected(item);
    setQuery(item.name);
    setResults([]);
  };
  const reset = () => {
    setQuery("");
    setResults([]);
    setSelected(null);
  };
  return { query, results, selected, search, pick, reset };
}

export default function DamagedReturns() {
  const router = useRouter();
  const { show } = useToast();

  const [records, setRecords] = useState<ReturnRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<RecordType | null>(null);
  const [saving, setSaving] = useState(false);

  // Shared fields
  const [partNumber, setPartNumber] = useState("");
  const [note, setNote] = useState("");
  // Customer return only
  const [condition, setCondition] = useState<"good" | "damaged">("good");
  const customerPicker = usePicker("/customers");
  // Vendor return only
  const vendorPicker = usePicker("/vendors");

  const load = useCallback(async () => {
    try {
      setRecords(await api.get<ReturnRecord[]>("/returns"));
    } catch {
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const openModal = (type: RecordType) => {
    setPartNumber("");
    setNote("");
    setCondition("good");
    customerPicker.reset();
    vendorPicker.reset();
    setModal(type);
  };

  const submit = async () => {
    if (!partNumber.trim()) {
      show("Part number required", "error");
      return;
    }
    setSaving(true);
    try {
      if (modal === "customer_return") {
        await api.post("/returns/customer", {
          part_number: partNumber.trim(),
          condition,
          customer_id: customerPicker.selected?.id || null,
          note: note.trim(),
        });
        show(condition === "good" ? "Return recorded — back in sellable stock" : "Return recorded — marked damaged", "success");
      } else if (modal === "damaged_stock") {
        await api.post("/returns/damaged", { part_number: partNumber.trim(), note: note.trim() });
        show("Unit marked damaged — removed from sellable stock", "success");
      } else if (modal === "vendor_return") {
        await api.post("/returns/vendor", {
          part_number: partNumber.trim(),
          vendor_id: vendorPicker.selected?.id || null,
          reason: note.trim(),
        });
        show("Vendor return recorded — unit removed from stock", "success");
      }
      setModal(null);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.message || "Save failed", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Damaged / Returns" subtitle="Customer returns, damaged stock, vendor returns" onBack={() => router.back()} />

      <View style={styles.actionRow}>
        <Pressable style={styles.actionBtn} onPress={() => openModal("customer_return")} testID="action-customer-return">
          <Ionicons name={TYPE_META.customer_return.icon} size={22} color={TYPE_META.customer_return.color} />
          <Text style={styles.actionText}>Customer{"\n"}Return</Text>
        </Pressable>
        <Pressable style={styles.actionBtn} onPress={() => openModal("damaged_stock")} testID="action-mark-damaged">
          <Ionicons name={TYPE_META.damaged_stock.icon} size={22} color={TYPE_META.damaged_stock.color} />
          <Text style={styles.actionText}>Mark{"\n"}Damaged</Text>
        </Pressable>
        <Pressable style={styles.actionBtn} onPress={() => openModal("vendor_return")} testID="action-vendor-return">
          <Ionicons name={TYPE_META.vendor_return.icon} size={22} color={TYPE_META.vendor_return.color} />
          <Text style={styles.actionText}>Vendor{"\n"}Return</Text>
        </Pressable>
      </View>

      {loading ? (
        <Loading />
      ) : records.length === 0 ? (
        <EmptyState icon="return-down-back-outline" title="No records yet" subtitle="Customer returns, damaged stock and vendor returns will show here" />
      ) : (
        <FlatList
          data={records}
          keyExtractor={(r) => r.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm }}
          renderItem={({ item }) => {
            const meta = TYPE_META[item.type];
            return (
              <View style={styles.row} testID={`return-${item.id}`}>
                <View style={[styles.avatar, { borderColor: meta.color }]}>
                  <Ionicons name={meta.icon} size={18} color={meta.color} />
                </View>
                <View style={{ flex: 1 }}>
                  <View style={styles.rowTop}>
                    <Text style={styles.pn}>{item.part_number}</Text>
                    <Text style={[styles.typeLabel, { color: meta.color }]}>{meta.label}</Text>
                  </View>
                  <Text style={styles.meta}>
                    {new Date(item.at).toLocaleString()}
                    {item.condition ? ` • ${item.condition}` : ""}
                  </Text>
                  {item.note ? <Text style={styles.note} numberOfLines={2}>{item.note}</Text> : null}
                </View>
              </View>
            );
          }}
        />
      )}

      <Modal visible={!!modal} transparent animationType="fade" onRequestClose={() => setModal(null)}>
        <View style={mstyles.wrap}>
          <View style={mstyles.box}>
            <ScrollView keyboardShouldPersistTaps="handled">
              <Text style={mstyles.title}>{modal ? TYPE_META[modal].label : ""}</Text>

              <Field label="Part number" value={partNumber} onChangeText={setPartNumber} autoCapitalize="characters" placeholder="e.g. 39100-2B000" testID="return-pn" />

              {modal === "customer_return" ? (
                <>
                  <Text style={mstyles.label}>Condition</Text>
                  <View style={mstyles.chipRow}>
                    <FilterChip label="Good (reusable)" active={condition === "good"} onPress={() => setCondition("good")} testID="return-condition-good" />
                    <FilterChip label="Damaged" active={condition === "damaged"} onPress={() => setCondition("damaged")} testID="return-condition-damaged" />
                  </View>
                  <Field
                    label="Customer (optional)"
                    value={customerPicker.query}
                    onChangeText={customerPicker.search}
                    placeholder="Search name or phone"
                    testID="return-customer-search"
                  />
                  {customerPicker.results.length ? (
                    <View style={mstyles.results}>
                      {customerPicker.results.slice(0, 5).map((c) => (
                        <Pressable key={c.id} style={mstyles.resultRow} onPress={() => customerPicker.pick(c)} testID={`return-customer-${c.id}`}>
                          <Text style={mstyles.resultText}>{c.name} · {c.phone}</Text>
                        </Pressable>
                      ))}
                    </View>
                  ) : null}
                  <Field label="Note (optional)" value={note} onChangeText={setNote} placeholder="Condition note" multiline testID="return-note" />
                </>
              ) : modal === "damaged_stock" ? (
                <Field label="Note (optional)" value={note} onChangeText={setNote} placeholder="What happened to it" multiline testID="return-note" />
              ) : modal === "vendor_return" ? (
                <>
                  <Field
                    label="Vendor (optional)"
                    value={vendorPicker.query}
                    onChangeText={vendorPicker.search}
                    placeholder="Search name or phone"
                    testID="return-vendor-search"
                  />
                  {vendorPicker.results.length ? (
                    <View style={mstyles.results}>
                      {vendorPicker.results.slice(0, 5).map((v) => (
                        <Pressable key={v.id} style={mstyles.resultRow} onPress={() => vendorPicker.pick(v)} testID={`return-vendor-${v.id}`}>
                          <Text style={mstyles.resultText}>{v.name} · {v.phone}</Text>
                        </Pressable>
                      ))}
                    </View>
                  ) : null}
                  <Field label="Reason (optional)" value={note} onChangeText={setNote} placeholder="Why it's going back" multiline testID="return-note" />
                </>
              ) : null}

              <View style={mstyles.row}>
                <Button title="Cancel" onPress={() => setModal(null)} variant="secondary" style={{ flex: 1 }} testID="return-cancel" />
                <Button title="Save" onPress={submit} loading={saving} style={{ flex: 1 }} testID="return-save" />
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  actionRow: { flexDirection: "row", gap: spacing.sm, padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.divider },
  actionBtn: {
    flex: 1, alignItems: "center", gap: spacing.xs, backgroundColor: colors.surface2,
    borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingVertical: spacing.md,
  },
  actionText: { color: colors.onSurface2, fontSize: font.sm - 1, fontWeight: "700", textAlign: "center" },
  row: { flexDirection: "row", alignItems: "flex-start", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  avatar: { width: 36, height: 36, borderRadius: 18, borderWidth: 1.5, alignItems: "center", justifyContent: "center", backgroundColor: colors.surface },
  rowTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: spacing.sm },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", flexShrink: 1 },
  typeLabel: { fontSize: font.sm - 1, fontWeight: "800" },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  note: { color: colors.onSurface2, fontSize: font.sm, marginTop: 4 },
});

const mstyles = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", alignItems: "center", justifyContent: "center", padding: spacing.xl },
  box: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.lg, padding: spacing.xl, width: "100%", maxHeight: "85%" },
  title: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", marginBottom: spacing.md },
  label: { color: colors.onSurface3, fontSize: font.sm, fontWeight: "700", letterSpacing: 0.3, marginBottom: spacing.xs },
  chipRow: { flexDirection: "row", gap: spacing.sm, marginBottom: spacing.md },
  results: { gap: spacing.xs, marginBottom: spacing.md, marginTop: -spacing.sm },
  resultRow: { backgroundColor: colors.surface3, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  resultText: { color: colors.onSurface2, fontSize: font.sm },
  row: { flexDirection: "row", gap: spacing.md, marginTop: spacing.md },
});
