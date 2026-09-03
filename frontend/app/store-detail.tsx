import { useCallback, useMemo, useState } from "react";
import { FlatList, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { EmptyState, FilterChip, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Tab = "inventory" | "buy" | "sell";

export default function StoreDetail() {
  const { id, name } = useLocalSearchParams<{ id: string; name: string }>();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>("inventory");
  const [rows, setRows] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const s = await api.get(`/stats?store_id=${id}`).catch(() => null);
      setStats(s);
      if (tab === "inventory") setRows(await api.get(`/inventory?store_id=${id}`));
      else setRows(await api.get(`/transactions?store_id=${id}&type=${tab}`));
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [id, tab]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const statCards = useMemo(
    () =>
      stats
        ? [
            { label: "Parts", value: stats.total_parts },
            { label: "In Stock", value: stats.in_stock_units },
            { label: "Sold", value: stats.sold_units },
            { label: "Buys", value: stats.total_buys },
            { label: "Sells", value: stats.total_sells },
          ]
        : [],
    [stats],
  );

  return (
    <View style={styles.flex}>
      <Header title={name || "Store"} subtitle="Admin view" onBack={() => router.back()} />

      {statCards.length ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.statsRow}>
          {statCards.map((s) => (
            <View key={s.label} style={styles.statCard}>
              <Text style={styles.statVal}>{s.value}</Text>
              <Text style={styles.statLbl}>{s.label}</Text>
            </View>
          ))}
        </ScrollView>
      ) : null}

      <View style={styles.tabs}>
        <FilterChip label="Inventory" active={tab === "inventory"} onPress={() => setTab("inventory")} testID="sd-inv" />
        <FilterChip label="Purchases" active={tab === "buy"} onPress={() => setTab("buy")} testID="sd-buy" />
        <FilterChip label="Sales" active={tab === "sell"} onPress={() => setTab("sell")} testID="sd-sell" />
      </View>

      {loading ? (
        <Loading />
      ) : rows.length === 0 ? (
        <EmptyState icon="documents-outline" title="Nothing here" subtitle="This store has no data in this tab" />
      ) : (
        <FlatList
          data={rows}
          keyExtractor={(r, i) => r.id || String(i)}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm }}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <View style={{ flex: 1 }}>
                <Text style={styles.pn}>{item.part_number}</Text>
                {item.part_name ? <Text style={styles.nm}>{item.part_name}</Text> : null}
                <Text style={styles.meta}>
                  {item.company || ""}
                  {item.category ? `  •  ${item.category}` : ""}
                  {item.at || item.created_at ? `  •  ${new Date(item.at || item.created_at).toLocaleDateString()}` : ""}
                </Text>
              </View>
              {item.condition ? <StatusChip status={item.condition} /> : null}
              {item.price != null ? <Text style={styles.price}>Rs.{item.price}</Text> : null}
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  statsRow: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  statCard: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingVertical: spacing.sm, paddingHorizontal: spacing.lg, alignItems: "center", minWidth: 76 },
  statVal: { color: colors.brand, fontSize: font.xl, fontWeight: "800" },
  statLbl: { color: colors.info, fontSize: font.sm - 1 },
  tabs: { flexDirection: "row", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingBottom: spacing.sm },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  pn: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  nm: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  meta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  price: { color: colors.success, fontSize: font.base, fontWeight: "800" },
});
