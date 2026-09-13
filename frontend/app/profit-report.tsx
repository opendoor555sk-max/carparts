import { useCallback, useEffect, useMemo, useState } from "react";
import { FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import DateTimePicker from "@react-native-community/datetimepicker";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Card, EmptyState, Field, FilterChip, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

// Reuses report.tsx's date-range chip pattern (resolveRange -> date_from/
// date_to on the query string) but adds "This Week" per this feature's own
// spec, and the aggregation is server-computed (revenue/cost/profit summed
// per part) rather than a flat transaction list, since a profit report is a
// summary, not a ledger.
const RANGES = [
  { key: "today", label: "Today" },
  { key: "week", label: "This Week" },
  { key: "month", label: "This Month" },
  { key: "all", label: "All" },
  { key: "custom", label: "Custom" },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

type PartRow = {
  part_number: string;
  part_name?: string;
  units_sold: number;
  revenue: number;
  cost: number;
  profit: number;
  unknown_cost_units: number;
};

type Report = {
  summary: {
    units_sold: number;
    total_revenue: number;
    total_cost: number;
    total_profit: number;
    units_with_unknown_cost: number;
  };
  by_part: PartRow[];
};

export default function ProfitReport() {
  const router = useRouter();
  const { show } = useToast();

  const [range, setRange] = useState("month");
  const [customFrom, setCustomFrom] = useState(iso(new Date()));
  const [customTo, setCustomTo] = useState(iso(new Date()));
  const [picker, setPicker] = useState<null | "from" | "to">(null);
  const [partFilter, setPartFilter] = useState("");
  const [report, setReport] = useState<Report | null>(null);
  const [loading, setLoading] = useState(true);

  const resolveRange = useCallback((): { from?: string; to?: string } => {
    const now = new Date();
    if (range === "today") return { from: iso(now), to: iso(now) };
    if (range === "week") {
      const start = new Date(now);
      start.setDate(now.getDate() - now.getDay()); // start of this week (Sun)
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
      if (partFilter.trim()) params.set("part_number", partFilter.trim());
      const qs = params.toString() ? `?${params.toString()}` : "";
      setReport(await api.get<Report>(`/reports/profit${qs}`));
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || "Load failed", "error");
      setReport(null);
    } finally {
      setLoading(false);
    }
  }, [resolveRange, partFilter, show]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  // Re-run automatically when a non-custom range is picked; Custom needs its
  // own Apply button (same UX as report.tsx) since two date taps happen
  // before there's a valid range to query.
  useEffect(() => {
    if (range !== "custom") load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range]);

  const topParts = useMemo(() => report?.by_part || [], [report]);

  return (
    <View style={styles.flex}>
      <Header title="Profit / Margin" subtitle="Revenue vs cost" onBack={() => router.back()} />

      <View style={styles.filters}>
        <Text style={styles.flabel}>DATE RANGE</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {RANGES.map((r) => (
            <FilterChip key={r.key} label={r.label} active={range === r.key} onPress={() => setRange(r.key)} testID={`profit-range-${r.key}`} />
          ))}
        </ScrollView>

        {range === "custom" ? (
          <View style={styles.customRow}>
            {Platform.OS === "web" ? (
              <>
                <TextInput style={styles.dateInput} value={customFrom} onChangeText={setCustomFrom} placeholder="YYYY-MM-DD" placeholderTextColor={colors.info} testID="profit-date-from" />
                <Text style={styles.toSep}>to</Text>
                <TextInput style={styles.dateInput} value={customTo} onChangeText={setCustomTo} placeholder="YYYY-MM-DD" placeholderTextColor={colors.info} testID="profit-date-to" />
                <Pressable style={styles.applyBtn} onPress={load} testID="profit-apply-custom">
                  <Text style={styles.applyText}>Apply</Text>
                </Pressable>
              </>
            ) : (
              <>
                <Pressable style={styles.dateBtn} onPress={() => setPicker("from")} testID="profit-date-from">
                  <Ionicons name="calendar" size={15} color={colors.brand} />
                  <Text style={styles.dateBtnText}>{customFrom}</Text>
                </Pressable>
                <Text style={styles.toSep}>to</Text>
                <Pressable style={styles.dateBtn} onPress={() => setPicker("to")} testID="profit-date-to">
                  <Ionicons name="calendar" size={15} color={colors.brand} />
                  <Text style={styles.dateBtnText}>{customTo}</Text>
                </Pressable>
                <Pressable style={styles.applyBtn} onPress={load} testID="profit-apply-custom">
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

        <Text style={styles.flabel}>PART NUMBER (optional)</Text>
        <View style={{ paddingHorizontal: spacing.lg }}>
          <Field value={partFilter} onChangeText={setPartFilter} onSubmitEditing={load} placeholder="Filter to one part number" autoCapitalize="characters" testID="profit-part-filter" />
        </View>
      </View>

      {loading ? (
        <Loading />
      ) : !report || report.summary.units_sold === 0 ? (
        <EmptyState icon="trending-up-outline" title="No sales in this range" subtitle="Try a wider date range" />
      ) : (
        <FlatList
          data={topParts}
          keyExtractor={(r) => r.part_number}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xxxl }}
          ListHeaderComponent={
            <View style={{ gap: spacing.md, marginBottom: spacing.md }}>
              <Card testID="profit-summary-card">
                <Text style={styles.cardTitle}>SUMMARY</Text>
                <View style={styles.summaryRow}>
                  <SummaryStat label="Revenue" value={report.summary.total_revenue} color={colors.onSurface} />
                  <SummaryStat label="Cost" value={report.summary.total_cost} color={colors.onSurface} />
                  <SummaryStat label="Profit" value={report.summary.total_profit} color={report.summary.total_profit >= 0 ? colors.success : colors.error} big />
                </View>
                <View style={styles.rowBetween}>
                  <Text style={styles.unitsText}>{report.summary.units_sold} unit(s) sold</Text>
                  {report.summary.units_with_unknown_cost > 0 ? (
                    <Text style={styles.unknownText}>
                      ⚠ {report.summary.units_with_unknown_cost} unit(s) had no recorded purchase price — excluded from cost/profit
                    </Text>
                  ) : null}
                </View>
              </Card>
              <Text style={styles.sectionLabel}>BY PART (top profit first)</Text>
            </View>
          }
          renderItem={({ item }) => (
            <View style={styles.partRow} testID={`profit-part-${item.part_number}`}>
              <View style={{ flex: 1 }}>
                <Text style={styles.pn}>{item.part_number}</Text>
                {item.part_name ? <Text style={styles.name}>{item.part_name}</Text> : null}
                <Text style={styles.meta}>
                  {item.units_sold} sold • Rev ₹{item.revenue.toFixed(2)} • Cost {item.unknown_cost_units === item.units_sold ? "unknown" : `₹${item.cost.toFixed(2)}`}
                </Text>
                {item.unknown_cost_units > 0 ? (
                  <Text style={styles.unknownInline}>{item.unknown_cost_units} unit(s) unknown cost</Text>
                ) : null}
              </View>
              <Text style={[styles.profit, { color: item.profit >= 0 ? colors.success : colors.error }]}>
                {item.unknown_cost_units === item.units_sold ? "—" : `₹${item.profit.toFixed(2)}`}
              </Text>
            </View>
          )}
        />
      )}
    </View>
  );
}

function SummaryStat({ label, value, color, big }: { label: string; value: number; color: string; big?: boolean }) {
  return (
    <View style={{ alignItems: "center", flex: 1 }}>
      <Text style={styles.statLabel}>{label}</Text>
      <Text style={[styles.statValue, { color }, big && styles.statValueBig]}>₹{value.toFixed(2)}</Text>
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
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  sectionLabel: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1 },
  summaryRow: { flexDirection: "row", justifyContent: "space-between" },
  statLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "700", letterSpacing: 0.5 },
  statValue: { fontSize: font.lg, fontWeight: "800", marginTop: 2 },
  statValueBig: { fontSize: font.xl },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: spacing.md, flexWrap: "wrap", gap: spacing.xs },
  unitsText: { color: colors.info, fontSize: font.sm, fontWeight: "700" },
  unknownText: { color: colors.warning, fontSize: font.sm - 1, flexShrink: 1 },
  partRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  pn: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  meta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  unknownInline: { color: colors.warning, fontSize: font.sm - 2, marginTop: 2 },
  profit: { fontSize: font.base, fontWeight: "900" },
});
