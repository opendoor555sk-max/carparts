import { useCallback, useState } from "react";
import { FlatList, RefreshControl, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, ConfirmModal, EmptyState, Header, Loading } from "@/src/components/ui";
import { OWNER_CONTACT } from "@/src/constants/owner";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

type OwnerStore = {
  id: string;
  name: string;
  admin_contact: string;
  user_count: number;
  part_count: number;
  status: "active" | "locked";
  created_at?: string | null;
  locked_at?: string | null;
  locked_by?: string | null;
};

export default function OwnerPanel() {
  const router = useRouter();
  const { user } = useAuth();
  const { t } = useLanguage();
  const { show } = useToast();

  const [stores, setStores] = useState<OwnerStore[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<OwnerStore | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Client-side only — a purely cosmetic gate deciding whether this screen's
  // content ever renders for THIS device. A non-owner reaching this route
  // directly (deep link, back-button trickery) sees nothing here and every
  // /owner/* call below would 403 anyway — real enforcement is exclusively
  // server-side (is_owner() against the caller's own DB record).
  const isOwner = !!user?.contact && user.contact === OWNER_CONTACT;

  const load = useCallback(async () => {
    if (!isOwner) {
      setLoading(false);
      return;
    }
    try {
      setStores(await api.get<OwnerStore[]>("/owner/stores"));
    } catch (e: any) {
      show(e?.message || t("common.loadFailed"), "error");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOwner]);

  useFocusEffect(
    useCallback(() => {
      if (!isOwner) {
        router.replace("/(tabs)/admin" as any);
        return;
      }
      setLoading(true);
      load();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOwner, load]),
  );

  const onRefresh = () => {
    setRefreshing(true);
    load();
  };

  const toggleLock = async (store: OwnerStore) => {
    setBusyId(store.id);
    try {
      const action = store.status === "locked" ? "unlock" : "lock";
      await api.post(`/owner/stores/${store.id}/${action}`);
      show(action === "lock" ? t("ownerPanel.lockedToast") : t("ownerPanel.unlockedToast"), "success");
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setBusyId(null);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.del(`/owner/stores/${deleteTarget.id}`, { confirm_name: deleteTarget.name });
      show(t("ownerPanel.deletedToast"), "success");
      setDeleteTarget(null);
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setDeleting(false);
    }
  };

  if (!isOwner) return null;

  return (
    <View style={styles.flex}>
      <Header title={t("ownerPanel.title")} subtitle={t("ownerPanel.subtitle")} onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : stores.length === 0 ? (
        <EmptyState icon="business-outline" title={t("ownerPanel.noStores")} />
      ) : (
        <FlatList
          data={stores}
          keyExtractor={(s) => s.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.brand} />}
          renderItem={({ item }) => {
            const locked = item.status === "locked";
            return (
              <View style={styles.card} testID={`owner-store-${item.id}`}>
                <View style={styles.rowTop}>
                  <Text style={styles.name} numberOfLines={1}>{item.name}</Text>
                  <View style={[styles.badge, { backgroundColor: locked ? colors.errorFaint : colors.successFaint }]}>
                    <Text style={[styles.badgeText, { color: locked ? colors.error : colors.success }]}>
                      {locked ? t("ownerPanel.locked") : t("ownerPanel.active")}
                    </Text>
                  </View>
                </View>
                {item.admin_contact ? <Text style={styles.meta}>{t("ownerPanel.adminContact")}: {item.admin_contact}</Text> : null}
                <View style={styles.statsRow}>
                  <View style={styles.stat}>
                    <Ionicons name="people" size={14} color={colors.info} />
                    <Text style={styles.statText}>{item.user_count} {t("ownerPanel.users")}</Text>
                  </View>
                  <View style={styles.stat}>
                    <Ionicons name="cube" size={14} color={colors.info} />
                    <Text style={styles.statText}>{item.part_count} {t("ownerPanel.parts")}</Text>
                  </View>
                </View>
                <View style={styles.actions}>
                  <Button
                    title={locked ? t("ownerPanel.unlock") : t("ownerPanel.lock")}
                    onPress={() => toggleLock(item)}
                    loading={busyId === item.id}
                    variant="secondary"
                    icon={locked ? "lock-open" : "lock-closed"}
                    style={{ flex: 1 }}
                    testID={`owner-toggle-${item.id}`}
                  />
                  <Button
                    title={t("ownerPanel.delete")}
                    onPress={() => setDeleteTarget(item)}
                    variant="danger"
                    icon="trash"
                    style={{ flex: 1 }}
                    testID={`owner-delete-${item.id}`}
                  />
                </View>
              </View>
            );
          }}
        />
      )}

      <ConfirmModal
        visible={!!deleteTarget}
        title={t("ownerPanel.deleteConfirmTitle")}
        message={deleteTarget ? t("ownerPanel.deleteConfirmMsg").replace("{name}", deleteTarget.name) : ""}
        confirmText={t("ownerPanel.delete")}
        danger
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
        confirmInput={deleteTarget ? { placeholder: deleteTarget.name, expectedValue: deleteTarget.name } : undefined}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  card: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
    gap: spacing.sm,
    ...shadow.sm,
  },
  rowTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.sm },
  name: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", flex: 1 },
  badge: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.pill },
  badgeText: { fontSize: font.sm - 1, fontWeight: "800" },
  meta: { color: colors.info, fontSize: font.sm },
  statsRow: { flexDirection: "row", gap: spacing.lg },
  stat: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  statText: { color: colors.info, fontSize: font.sm },
  actions: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.xs },
});
