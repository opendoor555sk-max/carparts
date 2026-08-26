import { useCallback, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { EmptyState, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function PartsList() {
  const { category, company, title } = useLocalSearchParams<{ category: string; company: string; title: string }>();
  const router = useRouter();
  const [parts, setParts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (category) params.append("category", category as string);
      if (company && company !== "All") params.append("company", company as string);
      setParts(await api.get(`/parts?${params.toString()}`));
    } catch {
    } finally {
      setLoading(false);
    }
  }, [category, company]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  return (
    <View style={styles.flex}>
      <Header title={(title as string) || "Parts"} subtitle="Part masters" onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : parts.length === 0 ? (
        <EmptyState icon="documents-outline" title="કોઈ part નથી" subtitle="આ category માં હજી part master નથી" />
      ) : (
        <FlatList
          data={parts}
          keyExtractor={(p) => p.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}
          renderItem={({ item }) => (
            <Pressable
              style={styles.row}
              onPress={() => router.push(`/part/${encodeURIComponent(item.part_number)}` as any)}
              testID={`partlist-${item.part_number}`}
            >
              <View style={{ flex: 1 }}>
                <Text style={styles.pn}>{item.part_number}</Text>
                {item.name ? <Text style={styles.name}>{item.name}</Text> : null}
                <Text style={styles.meta}>{item.company} • Stock: {item.stock_count ?? 0}</Text>
              </View>
              <StatusChip status={item.verification_status} />
              <Ionicons name="chevron-forward" size={18} color={colors.info} />
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  meta: { color: colors.info, fontSize: font.sm, marginTop: spacing.xs },
});
