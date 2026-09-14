import { useCallback, useEffect, useState } from "react";
import { FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import DateTimePicker from "@react-native-community/datetimepicker";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { EmptyState, FilterChip, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

// Same date-range chip pattern as profit-report.tsx (resolveRange ->
// date_from/date_to on the query string) applied to a flat list instead of
// an aggregation, since this is a log of damaged units, not a summary.
const RANGES = [
  { key: "today", label: "Today" },
  { key: "week", label: "This Week" },
  { key: "month", label: "This Month" },
  { key: "all", label: "All" },
  { key: "custom", label: "Custom" },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

type Damaged = {
  id: string;
  part_number: string;
  reason: string;
  marked_by: string;
  at: string;
};

export default function DamagedItems() {
  const router = useRouter();
  const { show } = useToast();

  const [range, setRange] = useState("month");
  const [customFrom, setCustomFrom] = useState(iso(new Date()));
  const [customTo, setCustomTo] = useState(iso(new Date()));
  const [picker, setPicker] = useState<null | "from" | "to">(null);
  const [items, setItems] = useState<Damaged[]>([]);
  const [loading, setLoading] = useState(true);

  const resolveRange = useCallback((): { from?: string; to?: string } => {
    const now = new Date();
    if (range === "today") return { from: iso(now), to: iso(now) };
    if (range === "week") {
      const start = new Date(now);
      start.setDate(now.getDate() - now.getDay());
      return { from: iso(start), to: iso(now) };
    }
    if (range === "month") return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(now) };
    if (range === "custom") return { from: customFrom, to: customTo };
    return {};
  }, [range, customFrom, customTo]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { from, to } = resolveRange();
      const params = new URLSearchParams();
      if (from) params.set("date_from", from);
      if (to) params.set("date_to", to);
      const qs = params.toString() ? `?${params.toString()}` : "";
      setItems(await api.get<Damaged[]>(`/damaged${qs}`));
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || "Load failed", "error");
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [resolveRange, show]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  useEffect(() => {
    if (range !== "custom") load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range]);

  return (
    <View style={styles.flex}>
      <Header title="Damaged Items" subtitle="Damaged / scrap log" onBack={() => router.back()} />

      <View style={styles.filters}>
        <Text style={styles.flabel}>DATE RANGE</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {RANGES.map((r) => (
            <FilterChip key={r.key} label={r.label} active={range === r.key} onPress={() => setRange(r.key)} testID={`damaged-range-${r.key}`} />
          ))}
        </ScrollView>

        {range === "custom" ? (
          <View style={styles.customRow}>
            {Platform.OS === "web" ? (
              <>
                <TextInput style={styles.dateInput} value={customFrom} onChangeText={setCustomFrom} placeholder="YYYY-MM-DD" placeholderTextColor={colors.info} testID="damaged-date-from" />
                <Text style={styles.toSep}>to</Text>
                <TextInput style={styles.dateInput} value={customTo} onChangeText={setCustomTo} placeholder="YYYY-MM-DD" placeholderTextColor={colors.info} testID="damaged-date-to" />
                <Pressable style={styles.applyBtn} onPress={load} testID="damaged-apply-custom">
                  <Text style={styles.applyText}>Apply</Text>
                </Pressable>
              </>
            ) : (
              <>
                <Pressable style={styles.dateBtn} onPress={() => setPicker("from")} testID="damaged-date-from">
                  <Ionicons name="calendar" size={15} color={colors.brand} />
                  <Text style={styles.dateBtnText}>{customFrom}</Text>
                </Pressable>
                <Text style={styles.toSep}>to</Text>
                <Pressable style={styles.dateBtn} onPress={() => setPicker("to")} testID="damaged-date-to">
                  <Ionicons name="calendar" size={15} color={colors.brand} />
                  <Text style={styles.dateBtnText}>{customTo}</Text>
                </Pressable>
                <Pressable style={styles.applyBtn} onPress={load} testID="damaged-apply-custom">
                  <Text style={styles.applyText}>Apply</Text>
                </Pressable>
              </>
            )}
          </View>
        ) : null}

        {picker ? (
          <DateTimePicker
            value={new Date(picker === "from" ? customFrom : customTo)}
            mode="date"
            onChange={(_e, d) => {
              setPicker(null);
              if (d) (picker === "from" ? setCustomFrom : setCustomTo)(iso(d));
            }}
          />
        ) : null}
      </View>

      {loading ? (
        <Loading />
      ) : items.length === 0 ? (
        <EmptyState icon="alert-circle-outline" title="No damaged items" subtitle="Nothing marked damaged in this range" />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(d) => d.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xxxl }}
          ListHeaderComponent={<Text style={styles.count}>{items.length} item(s)</Text>}
          renderItem={({ item }) => (
            <View style={styles.row} testID={`damaged-${item.id}`}>
              <View style={styles.avatar}>
                <Ionicons name="alert-circle" size={20} color={colors.error} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.pn}>{item.part_number}</Text>
                <Text style={styles.reason}>{item.reason}</Text>
                <Text style={styles.meta}>
                  {new Date(item.at).toLocaleString()} • {item.marked_by}
                </Text>
              </View>
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  filters: { paddingVertical: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.divider },
  flabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 0.5, paddingHorizontal: spacing.lg, marginTop: spacing.xs },
  chipRow: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.xs },
  customRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.xs },
  dateBtn: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  dateBtnText: { color: colors.onSurface, fontSize: font.sm, fontWeight: "700" },
  dateInput: { flex: 1, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, color: colors.onSurface, fontSize: font.sm },
  toSep: { color: colors.info, fontSize: font.sm },
  applyBtn: { backgroundColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  applyText: { color: colors.onBrand, fontWeight: "800", fontSize: font.sm },
  count: { color: colors.info, fontSize: font.sm, fontWeight: "700", marginBottom: spacing.xs },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: "#3a0f0d", alignItems: "center", justifyContent: "center" },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  reason: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  meta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
});
