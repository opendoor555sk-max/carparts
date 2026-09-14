import { useCallback, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { ConfirmModal, EmptyState, FilterChip, Header, Loading } from "@/src/components/ui";
import { printReport, brandingFromUser } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

type Txn = {
  id: string;
  type: "buy" | "sell";
  part_number: string;
  part_name?: string;
  price?: number | null;
  by?: string;
  at?: string;
  buyer?: string;
};

export default function History() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const { user } = useAuth();
  const { t } = useLanguage();
  const [tab, setTab] = useState<"buy" | "sell">("buy");
  const [txns, setTxns] = useState<Txn[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const [deleting, setDeleting] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setSelected({});
    try {
      const data = await api.get<Txn[]>(`/transactions?type=${tab}`);
      setTxns(data);
    } catch (e: any) {
      show(e?.message || t("common.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [tab, show, t]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const toggle = (id: string) => setSelected((s) => ({ ...s, [id]: !s[id] }));
  const selectedIds = Object.keys(selected).filter((k) => selected[k]);
  const allSelected = txns.length > 0 && selectedIds.length === txns.length;
  const toggleAll = () =>
    setSelected(allSelected ? {} : Object.fromEntries(txns.map((t) => [t.id, true])));

  const doDelete = async () => {
    if (selectedIds.length === 0) return;
    setDeleting(true);
    try {
      const res = await api.post("/transactions/delete", { ids: selectedIds, remove_stock: true });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      show(`${res.deleted} ${t("history.entriesPlusStock")} ${res.removed_units} ${t("history.stockDeleted")}`, "success");
      setConfirmOpen(false);
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header
        title={t("history.title")}
        subtitle={t("history.subtitle")}
        onBack={() => router.back()}
        right={
          txns.length > 0 ? (
            <View style={{ flexDirection: "row", alignItems: "center", gap: spacing.lg }}>
              <Pressable onPress={async () => printReport(await brandingFromUser(user), tab === "buy" ? t("history.purchaseHistory") : t("history.saleHistory"), txns, true)} testID="print-history">
                <Ionicons name="print" size={22} color={colors.brand} />
              </Pressable>
              <Pressable onPress={toggleAll} testID="select-all">
                <Text style={styles.selAll}>{allSelected ? t("history.clear") : t("common.all")}</Text>
              </Pressable>
            </View>
          ) : undefined
        }
      />
      <View style={styles.tabs}>
        <FilterChip label={t("buy.title")} active={tab === "buy"} onPress={() => setTab("buy")} testID="tab-buy" />
        <FilterChip label={t("sell.title")} active={tab === "sell"} onPress={() => setTab("sell")} testID="tab-sell" />
      </View>

      {loading ? (
        <Loading />
      ) : txns.length === 0 ? (
        <EmptyState icon="receipt-outline" title={t("history.noEntries")} subtitle={tab === "buy" ? t("history.noPurchases") : t("history.noSales")} />
      ) : (
        <FlatList
          data={txns}
          keyExtractor={(t) => t.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: insets.bottom + 100 }}
          renderItem={({ item }) => {
            const on = !!selected[item.id];
            return (
              <Pressable
                style={[styles.row, on && styles.rowOn]}
                onPress={() => toggle(item.id)}
                testID={`txn-${item.id}`}
              >
                <Ionicons
                  name={on ? "checkbox" : "square-outline"}
                  size={24}
                  color={on ? colors.brand : colors.info}
                />
                <View style={{ flex: 1 }}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                  {item.part_name ? <Text style={styles.name}>{item.part_name}</Text> : null}
                  <Text style={styles.meta}>
                    {item.by ? `${t("unlinkedStock.by")} ${item.by}` : ""}
                    {item.at ? `  •  ${new Date(item.at).toLocaleDateString()}` : ""}
                    {item.buyer ? `  •  ${item.buyer}` : ""}
                  </Text>
                </View>
                {item.price ? <Text style={styles.price}>₹{item.price}</Text> : null}
              </Pressable>
            );
          }}
        />
      )}

      {selectedIds.length > 0 ? (
        <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
          <Pressable style={styles.delBtn} onPress={() => setConfirmOpen(true)} disabled={deleting} testID="bulk-delete">
            <Ionicons name="trash" size={20} color={colors.onError} />
            <Text style={styles.delText}>
              {deleting ? t("history.deletingEllipsis") : `${t("common.delete")} ${selectedIds.length} ${t("history.entries")}`}
            </Text>
          </Pressable>
        </View>
      ) : null}

      <ConfirmModal
        visible={confirmOpen}
        title={`${t("common.delete")} ${selectedIds.length} ${t("history.entries")}?`}
        message={tab === "buy" ? t("history.deleteConfirmMsgBuy") : t("history.deleteConfirmMsgSell")}
        confirmText={t("common.delete")}
        danger
        loading={deleting}
        onConfirm={doDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  selAll: { color: colors.brand, fontSize: font.base, fontWeight: "800" },
  tabs: { flexDirection: "row", gap: spacing.sm, padding: spacing.lg, paddingBottom: spacing.sm, borderBottomWidth: 1, borderBottomColor: colors.divider },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  rowOn: { borderColor: colors.brand, backgroundColor: colors.brandFaint },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  price: { color: colors.success, fontSize: font.lg, fontWeight: "800" },
  bar: { position: "absolute", bottom: 0, left: 0, right: 0, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.lg },
  delBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.sm, backgroundColor: colors.error, borderRadius: radius.md, paddingVertical: spacing.md },
  delText: { color: colors.onError, fontSize: font.lg, fontWeight: "800" },
});
