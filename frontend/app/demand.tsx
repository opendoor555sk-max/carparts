import { useCallback, useState } from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { EmptyState, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Demand() {
  const router = useRouter();
  const [demand, setDemand] = useState<any[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [d, h] = await Promise.all([api.get("/demand"), api.get("/search-history")]);
      setDemand(d);
      setHistory(h);
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

  return (
    <View style={styles.flex}>
      <Header title="Demand & Search" subtitle="High-demand detection" onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : history.length === 0 ? (
        <EmptyState icon="trending-up" title="કોઈ search data નથી" subtitle="Search module વાપરો" />
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
                <Text style={styles.meta}>Searched {item.count}× • last: {item.last_status}</Text>
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
  hotBox: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.error, borderRadius: radius.md, padding: spacing.lg, marginBottom: spacing.md },
  hotHead: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: spacing.md },
  hotTitle: { color: colors.error, fontSize: font.sm, fontWeight: "800", letterSpacing: 0.5, flex: 1 },
  hotRow: { flexDirection: "row", justifyContent: "space-between", paddingVertical: spacing.xs },
  hotPn: { color: colors.onSurface, fontSize: font.base, fontWeight: "700" },
  hotCount: { color: colors.error, fontSize: font.sm, fontWeight: "700" },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
});
