import { useCallback, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Card, EmptyState, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type UnlinkedUnit = {
  id: string;
  part_number: string;
  condition: string;
  location?: Record<string, string>;
  assigned_location?: string | null;
  barcode?: string;
  created_at: string;
  added_by?: string;
};

export default function UnlinkedStock() {
  const router = useRouter();
  const { show } = useToast();
  const [units, setUnits] = useState<UnlinkedUnit[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const data = await api.get<UnlinkedUnit[]>("/inventory/unlinked-stock");
      setUnits(data);
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [show]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  // Opens the existing manual-buy screen pre-filled with this part number, so
  // the admin can properly record a purchase for a unit that has none.
  const linkUnit = (u: UnlinkedUnit) => {
    router.push(`/buy?pn=${encodeURIComponent(u.part_number)}` as any);
  };

  return (
    <View style={styles.flex}>
      <Header
        title="Unlinked Stock"
        subtitle="Units with no purchase record on file"
        onBack={() => router.back()}
      />
      {loading ? (
        <Loading text="Checking stock for untracked units…" />
      ) : units.length === 0 ? (
        <EmptyState
          icon="checkmark-done-circle"
          title="All clear"
          subtitle="Every active stock unit has a matching purchase record"
        />
      ) : (
        <FlatList
          data={units}
          keyExtractor={(u) => u.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
          ListHeaderComponent={
            <View style={styles.warnBanner} testID="unlinked-count">
              <Ionicons name="warning" size={18} color={colors.onWarning} />
              <Text style={styles.warnText}>
                {units.length} unit{units.length === 1 ? "" : "s"} found with no traceable buy transaction
              </Text>
            </View>
          }
          renderItem={({ item }) => (
            <Card testID={`unlinked-${item.id}`}>
              <View style={styles.row}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                  <Text style={styles.meta}>
                    {item.condition} • added {new Date(item.created_at).toLocaleDateString()}
                    {item.added_by ? ` by ${item.added_by}` : ""}
                  </Text>
                  {item.assigned_location ? (
                    <View style={styles.locRow}>
                      <Ionicons name="location" size={12} color={colors.info} />
                      <Text style={styles.locText}>{item.assigned_location}</Text>
                    </View>
                  ) : null}
                </View>
                <Pressable style={styles.linkBtn} onPress={() => linkUnit(item)} testID={`link-${item.id}`}>
                  <Ionicons name="link" size={16} color={colors.onBrand} />
                  <Text style={styles.linkBtnText}>Create Buy Entry</Text>
                </Pressable>
              </View>
            </Card>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  warnBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.warning,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.sm,
  },
  warnText: { flex: 1, color: colors.onWarning, fontWeight: "800", fontSize: font.sm },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  locRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: spacing.xs },
  locText: { color: colors.info, fontSize: font.sm },
  linkBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.brand,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  linkBtnText: { color: colors.onBrand, fontWeight: "800", fontSize: font.sm - 1 },
});
