import { useCallback, useMemo, useState } from "react";
import { FlatList, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useLanguage } from "@/src/context/LanguageContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, EmptyState, FilterChip, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

type Tab = "inventory" | "buy" | "sell" | "staff";

type StaffRow = {
  id: string;
  name: string;
  username: string;
  role: "admin" | "staff" | "super_admin";
  disabled: boolean;
  created_by?: { id: string; name: string; contact?: string } | null;
};

export default function StoreDetail() {
  const { id, name } = useLocalSearchParams<{ id: string; name: string }>();
  const router = useRouter();
  const { t } = useLanguage();
  const { show } = useToast();
  const [tab, setTab] = useState<Tab>("inventory");
  const [rows, setRows] = useState<any[]>([]);
  const [staff, setStaff] = useState<StaffRow[]>([]);
  const [staffBusyId, setStaffBusyId] = useState<string | null>(null);
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const s = await api.get(`/stats?store_id=${id}`).catch(() => null);
      setStats(s);
      if (tab === "staff") setStaff(await api.get<StaffRow[]>(`/owner/users?store_id=${id}`));
      else if (tab === "inventory") setRows(await api.get(`/inventory?store_id=${id}`));
      else setRows(await api.get(`/transactions?store_id=${id}&type=${tab}`));
    } catch {
      setRows([]);
      setStaff([]);
    } finally {
      setLoading(false);
    }
  }, [id, tab]);

  const toggleStaffActive = async (u: StaffRow) => {
    setStaffBusyId(u.id);
    try {
      await api.post(`/owner/users/${u.id}/${u.disabled ? "reactivate" : "deactivate"}`);
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setStaffBusyId(null);
    }
  };

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const statCards = useMemo(
    () =>
      stats
        ? [
            { label: t("parts.title"), value: stats.total_parts },
            { label: t("storeDetail.inStock"), value: stats.in_stock_units },
            { label: t("storeDetail.sold"), value: stats.sold_units },
            { label: t("storeDetail.buys"), value: stats.total_buys },
            { label: t("storeDetail.sells"), value: stats.total_sells },
          ]
        : [],
    [stats, t],
  );

  return (
    <View style={styles.flex}>
      <Header title={name || t("storeDetail.store")} subtitle={t("storeDetail.adminView")} onBack={() => router.back()} />

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
        <FilterChip label={t("tabs.inventory")} active={tab === "inventory"} onPress={() => setTab("inventory")} testID="sd-inv" />
        <FilterChip label={t("storeDetail.purchases")} active={tab === "buy"} onPress={() => setTab("buy")} testID="sd-buy" />
        <FilterChip label={t("storeDetail.sales")} active={tab === "sell"} onPress={() => setTab("sell")} testID="sd-sell" />
        <FilterChip label={t("ownerPanel.tabStaff")} active={tab === "staff"} onPress={() => setTab("staff")} testID="sd-staff" />
      </View>

      {loading ? (
        <Loading />
      ) : tab === "staff" ? (
        staff.length === 0 ? (
          <EmptyState icon="people-outline" title={t("ownerPanel.noStaff")} />
        ) : (
          <FlatList
            data={staff}
            keyExtractor={(u) => u.id}
            contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
            renderItem={({ item }) => (
              <View style={styles.staffCard} testID={`sd-staff-${item.id}`}>
                <View style={styles.rowTopStaff}>
                  <Text style={styles.pn}>{item.name}</Text>
                  <View style={[styles.badge, { backgroundColor: item.disabled ? colors.errorFaint : colors.successFaint }]}>
                    <Text style={[styles.badgeText, { color: item.disabled ? colors.error : colors.success }]}>
                      {item.disabled ? t("ownerPanel.staffDisabled") : t("ownerPanel.staffActive")}
                    </Text>
                  </View>
                </View>
                <Text style={styles.meta}>@{item.username} · {item.role}</Text>
                <Text style={styles.meta}>
                  {t("users.addedBy")}: {item.created_by?.name || t("users.addedByUnknown")}
                </Text>
                {item.role !== "super_admin" ? (
                  <Button
                    title={item.disabled ? t("ownerPanel.reactivate") : t("ownerPanel.deactivate")}
                    onPress={() => toggleStaffActive(item)}
                    loading={staffBusyId === item.id}
                    variant={item.disabled ? "secondary" : "danger"}
                    icon={item.disabled ? "checkmark-circle" : "ban"}
                    style={{ marginTop: spacing.xs }}
                    testID={`sd-staff-toggle-${item.id}`}
                  />
                ) : null}
              </View>
            )}
          />
        )
      ) : rows.length === 0 ? (
        <EmptyState icon="documents-outline" title={t("storeDetail.nothingHere")} subtitle={t("storeDetail.nothingHereSub")} />
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
  statCard: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingVertical: spacing.sm, paddingHorizontal: spacing.lg, alignItems: "center", minWidth: 76, ...shadow.sm },
  statVal: { color: colors.brand, fontSize: font.xl, fontWeight: "800" },
  statLbl: { color: colors.info, fontSize: font.sm - 1 },
  tabs: { flexDirection: "row", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingBottom: spacing.sm },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md, ...shadow.sm },
  pn: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", letterSpacing: 0.5 },
  nm: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  meta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  price: { color: colors.success, fontSize: font.base, fontWeight: "800" },
  staffCard: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg, gap: spacing.sm, ...shadow.sm },
  rowTopStaff: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.sm },
  badge: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.pill },
  badgeText: { fontSize: font.sm - 1, fontWeight: "800" },
});
