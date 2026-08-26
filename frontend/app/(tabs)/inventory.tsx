import { useCallback, useState } from "react";
import {
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { Header, StatusChip, Loading, EmptyState, FilterChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Unit = {
  id: string;
  part_number: string;
  condition: string;
  location: Record<string, string>;
  part_name?: string;
  company?: string;
};

const CONDITIONS = ["All", "Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown"];

export default function Inventory() {
  const router = useRouter();
  const [units, setUnits] = useState<Unit[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [cond, setCond] = useState("All");

  const load = useCallback(async () => {
    try {
      const q = cond === "All" ? "" : `?condition=${encodeURIComponent(cond)}`;
      const data = await api.get<Unit[]>(`/inventory${q}`);
      setUnits(data);
    } catch {
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [cond]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const locStr = (l: Record<string, string>) => {
    const parts = [l.rack, l.shelf, l.box, l.position].filter(Boolean);
    return parts.length ? parts.join(" → ") : "No location";
  };

  return (
    <View style={styles.flex}>
      <Header title="Inventory" subtitle="Physical stock units" />
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
        <EmptyState icon="cube-outline" title="કોઈ stock નથી" subtitle="Buy module થી stock add કરો" />
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
              </View>
              <StatusChip status={item.condition} />
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  chipScroller: { maxHeight: 56, borderBottomWidth: 1, borderBottomColor: colors.divider },
  chipRow: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, alignItems: "center" },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
  },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  locRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: spacing.xs },
  loc: { color: colors.info, fontSize: font.sm },
});
