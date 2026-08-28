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
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { ConfirmModal, Header, StatusChip, Loading, EmptyState, FilterChip } from "@/src/components/ui";
import { printInventory } from "@/src/utils/print";
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
  const { user } = useAuth();
  const { show } = useToast();
  const isAdmin = user?.role === "admin";
  const [units, setUnits] = useState<Unit[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [cond, setCond] = useState("All");
  const [busy, setBusy] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Unit | null>(null);

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

  const adjust = async (pn: string, delta: number) => {
    if (busy) return;
    setBusy(true);
    try {
      await api.post("/stock/adjust", { part_number: pn, delta });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      await load();
    } catch (e: any) {
      show(e?.message || "નિષ્ફળ", "error");
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = (u: Unit) => setPendingDelete(u);

  const performDelete = async () => {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await api.del(`/stock/unit/${pendingDelete.id}`);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      show("Unit deleted", "success");
      setPendingDelete(null);
      await load();
    } catch (e: any) {
      show(e?.message || "નિષ્ફળ", "error");
    } finally {
      setBusy(false);
    }
  };

  const locStr = (l: Record<string, string>) => {
    const parts = [l.rack, l.shelf, l.box, l.position].filter(Boolean);
    return parts.length ? parts.join(" → ") : "No location";
  };

  return (
    <View style={styles.flex}>
      <Header
        title="Inventory"
        subtitle="Physical stock units"
        right={
          units.length ? (
            <Pressable
              onPress={() => printInventory(user?.store_name || "", units)}
              hitSlop={12}
              testID="print-inventory"
            >
              <Ionicons name="print" size={22} color={colors.brand} />
            </Pressable>
          ) : undefined
        }
      />
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
            <View style={styles.card}>
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
              {isAdmin ? (
                <View style={styles.adminBar}>
                  <Pressable
                    style={styles.adminBtn}
                    onPress={() => adjust(item.part_number, -1)}
                    testID={`dec-${item.id}`}
                  >
                    <Ionicons name="remove" size={18} color={colors.warning} />
                    <Text style={[styles.adminBtnText, { color: colors.warning }]}>ઘટાડો</Text>
                  </Pressable>
                  <Pressable
                    style={styles.adminBtn}
                    onPress={() => adjust(item.part_number, 1)}
                    testID={`inc-${item.id}`}
                  >
                    <Ionicons name="add" size={18} color={colors.success} />
                    <Text style={[styles.adminBtnText, { color: colors.success }]}>વધારો</Text>
                  </Pressable>
                  <Pressable style={styles.adminBtn} onPress={() => confirmDelete(item)} testID={`del-${item.id}`}>
                    <Ionicons name="trash" size={16} color={colors.error} />
                    <Text style={[styles.adminBtnText, { color: colors.error }]}>Delete</Text>
                  </Pressable>
                </View>
              ) : null}
            </View>
          )}
        />
      )}

      <ConfirmModal
        visible={!!pendingDelete}
        title="Unit delete કરવું?"
        message={pendingDelete ? `${pendingDelete.part_number} નું આ એક unit કાયમ કાઢી નાખાશે.` : ""}
        confirmText="Delete"
        danger
        loading={busy}
        onConfirm={performDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  chipScroller: { maxHeight: 56, borderBottomWidth: 1, borderBottomColor: colors.divider },
  chipRow: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, alignItems: "center" },
  card: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, overflow: "hidden" },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.lg,
  },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  locRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: spacing.xs },
  loc: { color: colors.info, fontSize: font.sm },
  adminBar: { flexDirection: "row", borderTopWidth: 1, borderTopColor: colors.divider },
  adminBtn: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 4, paddingVertical: spacing.sm },
  adminBtnText: { fontSize: font.sm, fontWeight: "800" },
});
