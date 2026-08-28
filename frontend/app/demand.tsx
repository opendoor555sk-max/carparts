import { useCallback, useEffect, useState } from "react";
import { FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import DateTimePicker from "@react-native-community/datetimepicker";

import { api } from "@/src/api/client";
import { EmptyState, FilterChip, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

const RANGES = [
  { key: "all", label: "All" },
  { key: "month", label: "This Month" },
  { key: "year", label: "This Year" },
  { key: "today", label: "Today" },
  { key: "custom", label: "Custom" },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

export default function Demand() {
  const router = useRouter();
  const [demand, setDemand] = useState<any[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const [range, setRange] = useState("all");
  const [company, setCompany] = useState("All");
  const [category, setCategory] = useState("All");
  const [customFrom, setCustomFrom] = useState(iso(new Date()));
  const [customTo, setCustomTo] = useState(iso(new Date()));
  const [picker, setPicker] = useState<null | "from" | "to">(null);
  const [allCompanies, setAllCompanies] = useState<string[]>(["All"]);
  const [allCategories, setAllCategories] = useState<string[]>(["All"]);

  useEffect(() => {
    (async () => {
      try {
        const co = await api.get<string[]>("/companies");
        setAllCompanies(co.includes("All") ? co : ["All", ...co]);
      } catch {}
      try {
        const cat = await api.get<{ groups: { group: string; items: string[] }[] }>("/categories");
        setAllCategories(["All", ...cat.groups.flatMap((g) => g.items)]);
      } catch {}
    })();
  }, []);

  const resolveRange = useCallback((): { from?: string; to?: string } => {
    const now = new Date();
    if (range === "today") return { from: iso(now), to: iso(now) };
    if (range === "month") return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(now) };
    if (range === "year") return { from: iso(new Date(now.getFullYear(), 0, 1)), to: iso(now) };
    if (range === "custom") return { from: customFrom, to: customTo };
    return {};
  }, [range, customFrom, customTo]);

  const load = useCallback(async () => {
    try {
      const { from, to } = resolveRange();
      const params = new URLSearchParams();
      if (from) params.set("date_from", from);
      if (to) params.set("date_to", to);
      if (company !== "All") params.set("company", company);
      if (category !== "All") params.set("category", category);
      const qs = params.toString() ? `?${params.toString()}` : "";
      const [d, h] = await Promise.all([api.get("/demand"), api.get(`/search-history${qs}`)]);
      setDemand(d);
      setHistory(h);
    } catch {
    } finally {
      setLoading(false);
    }
  }, [resolveRange, company, category]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  return (
    <View style={styles.flex}>
      <Header title="Demand & Search" subtitle="High-demand detection" onBack={() => router.back()} />

      <View style={styles.filters}>
        <Text style={styles.flabel}>DATE</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {RANGES.map((r) => (
            <FilterChip key={r.key} label={r.label} active={range === r.key} onPress={() => setRange(r.key)} testID={`range-${r.key}`} />
          ))}
        </ScrollView>

        {range === "custom" ? (
          <View style={styles.customRow}>
            {Platform.OS === "web" ? (
              <>
                <TextInput style={styles.dateInput} value={customFrom} onChangeText={setCustomFrom} placeholder="YYYY-MM-DD" placeholderTextColor={colors.info} testID="date-from" />
                <Text style={styles.toSep}>to</Text>
                <TextInput style={styles.dateInput} value={customTo} onChangeText={setCustomTo} placeholder="YYYY-MM-DD" placeholderTextColor={colors.info} testID="date-to" />
                <Pressable style={styles.applyBtn} onPress={load} testID="apply-custom">
                  <Text style={styles.applyText}>Apply</Text>
                </Pressable>
              </>
            ) : (
              <>
                <Pressable style={styles.dateBtn} onPress={() => setPicker("from")} testID="date-from">
                  <Ionicons name="calendar" size={15} color={colors.brand} />
                  <Text style={styles.dateBtnText}>{customFrom}</Text>
                </Pressable>
                <Text style={styles.toSep}>to</Text>
                <Pressable style={styles.dateBtn} onPress={() => setPicker("to")} testID="date-to">
                  <Ionicons name="calendar" size={15} color={colors.brand} />
                  <Text style={styles.dateBtnText}>{customTo}</Text>
                </Pressable>
                <Pressable style={styles.applyBtn} onPress={load} testID="apply-custom">
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

        <Text style={styles.flabel}>COMPANY</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {allCompanies.map((c) => (
            <FilterChip key={c} label={c} active={company === c} onPress={() => setCompany(c)} testID={`co-${c}`} />
          ))}
        </ScrollView>
        <Text style={styles.flabel}>CATEGORY</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {allCategories.map((c) => (
            <FilterChip key={c} label={c} active={category === c} onPress={() => setCategory(c)} testID={`cat-${c}`} />
          ))}
        </ScrollView>
      </View>

      {loading ? (
        <Loading />
      ) : history.length === 0 ? (
        <EmptyState icon="trending-up" title="No search data" subtitle="Try another date / company / category" />
      ) : (
        <FlatList
          data={history}
          keyExtractor={(h) => h.part_number}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm }}
          ListHeaderComponent={
            demand.length ? (
              <View style={styles.hotBox}>
                <View style={styles.hotHead}>
                  <Ionicons name="flame" size={18} color={colors.error} />
                  <Text style={styles.hotTitle}>HIGH DEMAND — searched but no stock</Text>
                </View>
                {demand.map((d) => (
                  <View key={d.part_number} style={styles.hotRow}>
                    <Text style={styles.hotPn}>{d.part_number}</Text>
                    <Text style={styles.hotCount}>{d.count}× searched</Text>
                  </View>
                ))}
              </View>
            ) : null
          }
          renderItem={({ item }) => (
            <View style={styles.row} testID={`hist-${item.part_number}`}>
              <View style={{ flex: 1 }}>
                <Text style={styles.pn}>{item.part_number}</Text>
                {item.part_name ? <Text style={styles.name}>{item.part_name}</Text> : null}
                <Text style={styles.meta}>
                  Searched {item.count}× • last: {item.last_status}
                  {item.company && item.company !== "All" ? `  •  ${item.company}` : ""}
                </Text>
              </View>
              <StatusChip status={item.last_status} />
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
  hotBox: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.error, borderRadius: radius.md, padding: spacing.lg, marginBottom: spacing.md },
  hotHead: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: spacing.md },
  hotTitle: { color: colors.error, fontSize: font.sm, fontWeight: "800", letterSpacing: 0.5, flex: 1 },
  hotRow: { flexDirection: "row", justifyContent: "space-between", paddingVertical: spacing.xs },
  hotPn: { color: colors.onSurface, fontSize: font.base, fontWeight: "700" },
  hotCount: { color: colors.error, fontSize: font.sm, fontWeight: "700" },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  name: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
});
