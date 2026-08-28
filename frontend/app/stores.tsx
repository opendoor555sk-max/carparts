import { useCallback, useState } from "react";
import { FlatList, RefreshControl, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { Header, Loading, EmptyState } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Store = {
  id: string;
  name: string;
  created_at: string;
  users: number;
  parts: number;
  in_stock: number;
  owner: { name?: string; username?: string };
};

export default function Stores() {
  const router = useRouter();
  const [stores, setStores] = useState<Store[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      setStores(await api.get<Store[]>("/admin/stores"));
    } catch {
    } finally {
      setLoading(false);
      setRefreshing(false);
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
      <Header title="All Stores" subtitle="Super Admin — બધા stores" onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : stores.length === 0 ? (
        <EmptyState icon="storefront-outline" title="કોઈ store નથી" subtitle="Sign Up થી પહેલું store બનશે" />
      ) : (
        <FlatList
          data={stores}
          keyExtractor={(s) => s.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}
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
              <View style={styles.rowTop}>
                <View style={styles.iconBox}>
                  <Ionicons name="storefront" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.name}>{item.name}</Text>
                  <Text style={styles.owner}>
                    {item.owner?.name || "-"} ({item.owner?.username || "-"})
                  </Text>
                </View>
              </View>
              <View style={styles.stats}>
                <Stat label="Users" value={item.users} icon="people" />
                <Stat label="Parts" value={item.parts} icon="documents" />
                <Stat label="In Stock" value={item.in_stock} icon="cube" />
              </View>
            </View>
          )}
        />
      )}
    </View>
  );
}

function Stat({ label, value, icon }: { label: string; value: number; icon: keyof typeof Ionicons.glyphMap }) {
  return (
    <View style={styles.stat}>
      <Ionicons name={icon} size={16} color={colors.info} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  card: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg, gap: spacing.md },
  rowTop: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  iconBox: {
    width: 44,
    height: 44,
    borderRadius: radius.sm,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.brandFaint,
    alignItems: "center",
    justifyContent: "center",
  },
  name: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  owner: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  stats: { flexDirection: "row", gap: spacing.md },
  stat: { flex: 1, alignItems: "center", backgroundColor: colors.surface, borderRadius: radius.sm, paddingVertical: spacing.sm, gap: 2 },
  statValue: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  statLabel: { color: colors.info, fontSize: font.sm - 1 },
});
