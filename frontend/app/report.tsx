import { useCallback, useEffect, useMemo, useState } from "react";
import { FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import DateTimePicker from "@react-native-community/datetimepicker";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { EmptyState, FilterChip, Header, Loading, StatusChip } from "@/src/components/ui";
import { printReport, brandingFromUser } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

type Item = {
  id: string;
  part_number: string;
  part_name?: string;
  company?: string;
  category?: string;
  condition?: string;
  price?: number | null;
  at?: string;
  created_at?: string;
  buyer?: string;
};

type Mode = "buy" | "sell" | "stock";

const RANGES = [
  { key: "all", label: "All" },
  { key: "month", label: "This Month" },
  { key: "year", label: "This Year" },
  { key: "today", label: "Today" },
  { key: "custom", label: "Custom" },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

const TITLES: Record<Mode, string> = {
  buy: "Purchases",
  sell: "Sales",
  stock: "Stock Report",
};

export default function Report() {
  const { mode = "buy" } = useLocalSearchParams<{ mode: Mode }>();
  const m = (["buy", "sell", "stock"].includes(mode as string) ? mode : "buy") as Mode;
  const router = useRouter();
  const { user, can } = useAuth();
  const { show } = useToast();
  const showPrice = can("view_price");

  const [items, setItems] = useState<Item[]>([]);
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
        const flat = cat.groups.flatMap((g) => g.items);
        setAllCategories(["All", ...flat]);
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
    setLoading(true);
    try {
      const { from, to } = resolveRange();
      const params = new URLSearchParams();
      if (from) params.set("date_from", from);
      if (to) params.set("date_to", to);
      if (company !== "All") params.set("company", company);
      if (category !== "All") params.set("category", category);
      const qs = params.toString() ? `?${params.toString()}` : "";
      if (m === "stock") {
        setItems(await api.get<Item[]>(`/inventory${qs}`));
      } else {
        const sep = qs ? "&" : "?";
        setItems(await api.get<Item[]>(`/transactions${qs}${sep}type=${m}`));
      }
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [m, resolveRange, company, category, show]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const companies = allCompanies;
  const categories = allCategories;

  const sections = useMemo(() => {
    const g: Record<string, Record<string, Item[]>> = {};
    for (const it of items) {
      const co = it.company || "All";
      const cat = it.category || "Uncategorized";
      g[co] = g[co] || {};
      g[co][cat] = g[co][cat] || [];
      g[co][cat].push(it);
    }
    const rows: { type: "company" | "category" | "item"; key: string; label?: string; count?: number; item?: Item }[] = [];
    Object.keys(g).sort().forEach((co) => {
      rows.push({ type: "company", key: `co-${co}`, label: co });
      Object.keys(g[co]).sort().forEach((cat) => {
        rows.push({ type: "category", key: `cat-${co}-${cat}`, label: cat, count: g[co][cat].length });
        g[co][cat].forEach((it) => rows.push({ type: "item", key: `it-${it.id}`, item: it }));
      });
    });
    return rows;
  }, [items]);

  const total = useMemo(() => items.reduce((s, t) => s + (Number(t.price) || 0), 0), [items]);

  return (
    <View style={styles.flex}>
      <Header
        title={TITLES[m]}
        subtitle={user?.store_name}
        onBack={() => router.back()}
        right={
          items.length ? (
            <Pressable
              onPress={async () => printReport(await brandingFromUser(user), TITLES[m], items, showPrice && m !== "stock")}
              testID="print-report"
            >
              <Ionicons name="print" size={22} color={colors.brand} />
            </Pressable>
          ) : undefined
        }
      />

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
          {companies.map((c) => (
            <FilterChip key={c} label={c} active={company === c} onPress={() => setCompany(c)} testID={`co-${c}`} />
          ))}
        </ScrollView>
        <Text style={styles.flabel}>CATEGORY</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>
          {categories.map((c) => (
            <FilterChip key={c} label={c} active={category === c} onPress={() => setCategory(c)} testID={`cat-${c}`} />
          ))}
        </ScrollView>
      </View>

      {loading ? (
        <Loading />
      ) : items.length === 0 ? (
        <EmptyState icon="documents-outline" title="Nothing found" subtitle="Try another date / company / category" />
      ) : (
        <>
          <View style={styles.summary}>
            <Text style={styles.summaryText}>{items.length} items</Text>
            {showPrice && m !== "stock" ? <Text style={styles.summaryTotal}>Total Rs.{total}</Text> : null}
          </View>
          <FlatList
            data={sections}
            keyExtractor={(r) => r.key}
            contentContainerStyle={{ padding: spacing.lg, paddingTop: spacing.sm, gap: spacing.xs }}
            renderItem={({ item: row }) => {
              if (row.type === "company")
                return (
                  <View style={styles.coHead}>
                    <Ionicons name="business" size={16} color={colors.brand} />
                    <Text style={styles.coText}>{row.label}</Text>
                  </View>
                );
              if (row.type === "category")
                return (
                  <Text style={styles.catText}>
                    {row.label} <Text style={styles.catCount}>({row.count})</Text>
                  </Text>
                );
              const it = row.item!;
              return (
                <View style={styles.itemRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.pn}>{it.part_number}</Text>
                    {it.part_name ? <Text style={styles.name}>{it.part_name}</Text> : null}
                    <Text style={styles.meta}>
                      {it.at || it.created_at ? new Date(it.at || it.created_at || "").toLocaleDateString() : ""}
                      {it.buyer ? `  •  ${it.buyer}` : ""}
                    </Text>
                  </View>
                  {it.condition ? <StatusChip status={it.condition} /> : null}
                  {showPrice && it.price != null ? <Text style={styles.price}>Rs.{it.price}</Text> : null}
                </View>
              );
            }}
          />
        </>
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
  summary: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingHorizontal: spacing.lg, paddingTop: spacing.md },
  summaryText: { color: colors.info, fontSize: font.base, fontWeight: "700" },
  summaryTotal: { color: colors.success, fontSize: font.lg, fontWeight: "800" },
  coHead: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.md, marginBottom: spacing.xs },
  coText: { color: colors.brand, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  catText: { color: colors.onSurface3, fontSize: font.sm, fontWeight: "800", marginTop: spacing.xs, marginLeft: spacing.sm },
  catCount: { color: colors.info, fontWeight: "700" },
  itemRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  pn: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  meta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  price: { color: colors.success, fontSize: font.base, fontWeight: "800" },
});
