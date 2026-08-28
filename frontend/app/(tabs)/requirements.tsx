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
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Header, StatusChip, Loading, EmptyState, FilterChip } from "@/src/components/ui";
import { printRequirements, brandingFromUser } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

type Req = {
  id: string;
  part_number: string;
  name?: string;
  company?: string;
  priority: string;
  quantity: number;
  status: string;
  stock_count?: number;
  note?: string;
};

const STATUSES = ["All", "Pending", "Purchased", "Completed", "Cancelled"];
const NEXT: Record<string, string> = { Pending: "Purchased", Purchased: "Completed", Completed: "Pending" };

export default function Requirements() {
  const router = useRouter();
  const { show } = useToast();
  const { user } = useAuth();
  const [reqs, setReqs] = useState<Req[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [status, setStatus] = useState("All");

  const load = useCallback(async () => {
    try {
      const q = status === "All" ? "" : `?status=${status}`;
      setReqs(await api.get<Req[]>(`/requirements${q}`));
    } catch {
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [status]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const cycle = async (r: Req) => {
    const next = NEXT[r.status] || "Pending";
    try {
      await api.patch(`/requirements/${r.id}`, { status: next });
      show(`Status: ${next}`, "success");
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };

  const prColor: Record<string, string> = { High: colors.error, Medium: colors.warning, Low: colors.info };

  return (
    <View style={styles.flex}>
      <Header
        title="Requirements"
        subtitle="જરૂરિયાત list"
        right={
          <View style={{ flexDirection: "row", alignItems: "center", gap: spacing.md }}>
            {reqs.length ? (
              <Pressable onPress={async () => printRequirements(await brandingFromUser(user), reqs)} hitSlop={10} testID="print-requirements">
                <Ionicons name="print" size={22} color={colors.brand} />
              </Pressable>
            ) : null}
            <Pressable
              onPress={() => router.push("/scan?mode=requirement" as any)}
              style={styles.addBtn}
              testID="add-requirement"
            >
              <Ionicons name="add" size={22} color={colors.onBrand} />
            </Pressable>
          </View>
        }
      />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.chipScroller}
        contentContainerStyle={styles.chipRow}
      >
        {STATUSES.map((s) => (
          <FilterChip key={s} label={s} active={status === s} onPress={() => setStatus(s)} testID={`reqstatus-${s}`} />
        ))}
      </ScrollView>

      {loading ? (
        <Loading />
      ) : reqs.length === 0 ? (
        <EmptyState icon="list-outline" title="કોઈ requirement નથી" subtitle="ઉપર + button થી add કરો" />
      ) : (
        <FlatList
          data={reqs}
          keyExtractor={(r) => r.id}
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
            <View style={styles.row} testID={`req-${item.id}`}>
              <View style={{ flex: 1 }}>
                <View style={styles.pnRow}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                  <View style={[styles.dot, { backgroundColor: prColor[item.priority] || colors.info }]} />
                  <Text style={[styles.pr, { color: prColor[item.priority] || colors.info }]}>{item.priority}</Text>
                </View>
                {item.name ? <Text style={styles.name}>{item.name}</Text> : null}
                <Text style={styles.meta}>
                  Qty: {item.quantity} • In stock: {item.stock_count ?? 0}
                </Text>
              </View>
              <Pressable onPress={() => cycle(item)} testID={`req-cycle-${item.id}`}>
                <StatusChip status={item.status} />
              </Pressable>
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  addBtn: {
    width: 36,
    height: 36,
    borderRadius: radius.sm,
    backgroundColor: colors.brand,
    alignItems: "center",
    justifyContent: "center",
  },
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
  pnRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  dot: { width: 8, height: 8, borderRadius: 4 },
  pr: { fontSize: font.sm, fontWeight: "700" },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  meta: { color: colors.info, fontSize: font.sm, marginTop: spacing.xs },
});
