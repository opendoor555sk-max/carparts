import { useCallback, useState } from "react";
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
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

type StoreRequestStatus = "pending" | "otp_generated" | "verified" | "expired";

type OwnerStoreRequest = {
  id: string;
  name: string;
  mobile: string;
  status: StoreRequestStatus;
  created_at: string;
  verified_at?: string | null;
};

type OwnerUser = {
  id: string;
  name: string;
  username: string;
  role: "admin" | "staff" | "super_admin";
  store_id: string | null;
  store_name: string;
  disabled: boolean;
  created_at?: string | null;
  created_by?: { id: string; name: string; contact?: string } | null;
};

type SearchLog = {
  id: string;
  user_id: string;
  user_name: string;
  role?: string;
  store_id: string | null;
  store_name?: string;
  part_number_searched: string;
  gps: string | null;
  gps_coord: { lat: number; lng: number } | null;
  created_at: string;
};

type Tab = "stores" | "requests" | "staff" | "logs";
const LOGS_PAGE_SIZE = 50;

export default function OwnerPanel() {
  const router = useRouter();
  const { user } = useAuth();
  const { t } = useLanguage();
  const { show } = useToast();

  const [tab, setTab] = useState<Tab>("stores");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [stores, setStores] = useState<OwnerStore[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<OwnerStore | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [requests, setRequests] = useState<OwnerStoreRequest[]>([]);
  const [generatingId, setGeneratingId] = useState<string | null>(null);
  const [otpById, setOtpById] = useState<Record<string, { otp: string; expires_at: string }>>({});
  const [deletingRequestId, setDeletingRequestId] = useState<string | null>(null);

  const [staff, setStaff] = useState<OwnerUser[]>([]);
  const [staffBusyId, setStaffBusyId] = useState<string | null>(null);

  const [logs, setLogs] = useState<SearchLog[]>([]);
  const [logsTotal, setLogsTotal] = useState(0);
  const [logsPage, setLogsPage] = useState(1);
  const [loadingMoreLogs, setLoadingMoreLogs] = useState(false);

  // Client-side only — a purely cosmetic gate deciding whether this screen's
  // content ever renders for THIS device. A non-owner reaching this route
  // directly (deep link, back-button trickery) sees nothing here and every
  // /owner/* call below would 403 anyway — real enforcement is exclusively
  // server-side (is_owner() against the caller's own DB record).
  const isOwner = !!user?.contact && user.contact === OWNER_CONTACT;

  const load = useCallback(async (which: Tab) => {
    if (!isOwner) {
      setLoading(false);
      return;
    }
    try {
      if (which === "stores") setStores(await api.get<OwnerStore[]>("/owner/stores"));
      else if (which === "requests") setRequests(await api.get<OwnerStoreRequest[]>("/owner/store-requests"));
      else if (which === "staff") setStaff(await api.get<OwnerUser[]>("/owner/users"));
      else {
        const res = await api.get<{ items: SearchLog[]; total: number }>(
          `/owner/search-logs?page=1&page_size=${LOGS_PAGE_SIZE}`,
        );
        setLogs(res.items);
        setLogsTotal(res.total);
        setLogsPage(1);
      }
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
      load(tab);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOwner, tab, load]),
  );

  const onRefresh = () => {
    setRefreshing(true);
    load(tab);
  };

  const switchTab = (next: Tab) => {
    setTab(next);
    setLoading(true);
  };

  const toggleLock = async (store: OwnerStore) => {
    setBusyId(store.id);
    try {
      const action = store.status === "locked" ? "unlock" : "lock";
      await api.post(`/owner/stores/${store.id}/${action}`);
      show(action === "lock" ? t("ownerPanel.lockedToast") : t("ownerPanel.unlockedToast"), "success");
      load("stores");
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
      load("stores");
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setDeleting(false);
    }
  };

  const generateOtp = async (req: OwnerStoreRequest) => {
    setGeneratingId(req.id);
    try {
      const res = await api.post<{ ok: boolean; otp: string; expires_at: string }>(
        `/owner/store-requests/${req.id}/generate-otp`,
      );
      setOtpById((m) => ({ ...m, [req.id]: { otp: res.otp, expires_at: res.expires_at } }));
      load("requests");
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setGeneratingId(null);
    }
  };

  const deleteRequest = async (req: OwnerStoreRequest) => {
    setDeletingRequestId(req.id);
    try {
      await api.del(`/owner/store-requests/${req.id}`);
      show(t("ownerPanel.reqDeletedToast"), "success");
      load("requests");
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setDeletingRequestId(null);
    }
  };

  const toggleStaffActive = async (u: OwnerUser) => {
    setStaffBusyId(u.id);
    try {
      await api.post(`/owner/users/${u.id}/${u.disabled ? "reactivate" : "deactivate"}`);
      show(u.disabled ? t("ownerPanel.reactivatedToast") : t("ownerPanel.deactivatedToast"), "success");
      load("staff");
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setStaffBusyId(null);
    }
  };

  const loadMoreLogs = async () => {
    setLoadingMoreLogs(true);
    try {
      const next = logsPage + 1;
      const res = await api.get<{ items: SearchLog[]; total: number }>(
        `/owner/search-logs?page=${next}&page_size=${LOGS_PAGE_SIZE}`,
      );
      setLogs((cur) => [...cur, ...res.items]);
      setLogsTotal(res.total);
      setLogsPage(next);
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setLoadingMoreLogs(false);
    }
  };

  if (!isOwner) return null;

  const requestStatusColors: Record<StoreRequestStatus, { bg: string; fg: string }> = {
    pending: { bg: colors.surface3, fg: colors.info },
    otp_generated: { bg: colors.brandFaint, fg: colors.brand },
    verified: { bg: colors.successFaint, fg: colors.success },
    expired: { bg: colors.errorFaint, fg: colors.error },
  };
  const requestStatusLabel: Record<StoreRequestStatus, string> = {
    pending: t("ownerPanel.reqPending"),
    otp_generated: t("ownerPanel.reqOtpGenerated"),
    verified: t("ownerPanel.reqVerified"),
    expired: t("ownerPanel.reqExpired"),
  };

  return (
    <View style={styles.flex}>
      <Header title={t("ownerPanel.title")} subtitle={t("ownerPanel.subtitle")} onBack={() => router.back()} />

      <View style={styles.tabBar}>
        {(["stores", "requests", "staff", "logs"] as Tab[]).map((tb) => (
          <Pressable
            key={tb}
            onPress={() => switchTab(tb)}
            style={[styles.tabBtn, tab === tb && styles.tabBtnActive]}
            testID={`owner-tab-${tb}`}
          >
            <Text style={[styles.tabText, tab === tb && styles.tabTextActive]}>
              {tb === "stores"
                ? t("ownerPanel.tabStores")
                : tb === "requests"
                  ? t("ownerPanel.tabRequests")
                  : tb === "staff"
                    ? t("ownerPanel.tabStaff")
                    : t("ownerPanel.tabLogs")}
            </Text>
          </Pressable>
        ))}
      </View>

      {loading ? (
        <Loading />
      ) : tab === "stores" ? (
        stores.length === 0 ? (
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
                <Pressable
                  style={styles.card}
                  onPress={() => router.push(`/store-detail?id=${item.id}&name=${encodeURIComponent(item.name)}` as any)}
                  testID={`owner-store-${item.id}`}
                >
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
                  {/* Buttons live inside the same Pressable card, so each
                      stops its own tap from bubbling up to the card's
                      onPress (which would otherwise also navigate to
                      store-detail every time Lock/Delete is tapped). */}
                  <View
                    style={styles.actions}
                    onStartShouldSetResponder={() => true}
                    onTouchEnd={(e) => e.stopPropagation()}
                  >
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
                </Pressable>
              );
            }}
          />
        )
      ) : tab === "requests" ? (
        requests.length === 0 ? (
          <EmptyState icon="mail-outline" title={t("ownerPanel.noRequests")} />
        ) : (
          <FlatList
            data={requests}
            keyExtractor={(r) => r.id}
            contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.brand} />}
            renderItem={({ item }) => {
              const sc = requestStatusColors[item.status];
              const live = otpById[item.id];
              const canGenerate = item.status !== "verified";
              return (
                <View style={styles.card} testID={`owner-request-${item.id}`}>
                  <View style={styles.rowTop}>
                    <Text style={styles.name} numberOfLines={1}>{item.name}</Text>
                    <View style={[styles.badge, { backgroundColor: sc.bg }]}>
                      <Text style={[styles.badgeText, { color: sc.fg }]}>{requestStatusLabel[item.status]}</Text>
                    </View>
                  </View>
                  <Text style={styles.meta}>{item.mobile}</Text>

                  {live ? (
                    <View style={styles.otpBox}>
                      <Text style={styles.otpLabel}>{t("ownerPanel.otpLabel")}</Text>
                      <Text style={styles.otpValue} selectable testID={`owner-request-otp-${item.id}`}>{live.otp}</Text>
                      <Text style={styles.otpHint}>{t("ownerPanel.otpCopyHint")}</Text>
                      <Text style={styles.otpExpiry}>{t("ownerPanel.otpExpiresLabel")}: {new Date(live.expires_at).toLocaleTimeString()}</Text>
                    </View>
                  ) : null}

                  {item.status === "verified" ? (
                    <Text style={styles.adminNote}>{t("ownerPanel.reqCompleted")}</Text>
                  ) : (
                    <View style={{ flexDirection: "row", gap: spacing.sm, marginTop: spacing.xs }}>
                      {canGenerate ? (
                        <Button
                          title={item.status === "pending" ? t("ownerPanel.generateOtp") : t("ownerPanel.regenerateOtp")}
                          onPress={() => generateOtp(item)}
                          loading={generatingId === item.id}
                          icon="key"
                          style={{ flex: 1 }}
                          testID={`owner-generate-otp-${item.id}`}
                        />
                      ) : null}
                      <Button
                        title={t("ownerPanel.reqDelete")}
                        onPress={() => deleteRequest(item)}
                        loading={deletingRequestId === item.id}
                        variant="danger"
                        icon="trash"
                        style={{ flex: 1 }}
                        testID={`owner-delete-request-${item.id}`}
                      />
                    </View>
                  )}
                </View>
              );
            }}
          />
        )
      ) : tab === "staff" ? (
        staff.length === 0 ? (
          <EmptyState icon="people-outline" title={t("ownerPanel.noStaff")} />
        ) : (
          <FlatList
            data={staff}
            keyExtractor={(u) => u.id}
            contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
            ListHeaderComponent={
              <View style={styles.hint}>
                <Ionicons name="information-circle-outline" size={14} color={colors.info} />
                <Text style={styles.hintText}>{t("ownerPanel.staffHint")}</Text>
              </View>
            }
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.brand} />}
            renderItem={({ item }) => (
              <View style={styles.card} testID={`owner-staff-${item.id}`}>
                <View style={styles.rowTop}>
                  <Text style={styles.name} numberOfLines={1}>{item.name}</Text>
                  <View style={[styles.badge, { backgroundColor: item.disabled ? colors.errorFaint : colors.successFaint }]}>
                    <Text style={[styles.badgeText, { color: item.disabled ? colors.error : colors.success }]}>
                      {item.disabled ? t("ownerPanel.staffDisabled") : t("ownerPanel.staffActive")}
                    </Text>
                  </View>
                </View>
                <Text style={styles.meta}>@{item.username} · {item.role}</Text>
                <Text style={styles.meta}>{t("ownerPanel.staffStore")}: {item.store_name || "—"}</Text>
                <Text style={styles.meta}>
                  {t("users.addedBy")}: {item.created_by?.name || t("users.addedByUnknown")}
                </Text>
                <View style={styles.actions}>
                  <Button
                    title={item.disabled ? t("ownerPanel.reactivate") : t("ownerPanel.deactivate")}
                    onPress={() => toggleStaffActive(item)}
                    loading={staffBusyId === item.id}
                    variant={item.disabled ? "secondary" : "danger"}
                    icon={item.disabled ? "checkmark-circle" : "ban"}
                    style={{ flex: 1 }}
                    testID={`owner-staff-toggle-${item.id}`}
                  />
                </View>
              </View>
            )}
          />
        )
      ) : logs.length === 0 ? (
        <EmptyState icon="time-outline" title={t("searchLogs.empty")} />
      ) : (
        <FlatList
          data={logs}
          keyExtractor={(l) => l.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.brand} />}
          renderItem={({ item }) => (
            <View style={styles.card} testID={`owner-search-log-${item.id}`}>
              <View style={styles.rowTop}>
                <Text style={styles.name} numberOfLines={1}>{item.user_name}</Text>
                <Text style={styles.meta}>{new Date(item.created_at).toLocaleString()}</Text>
              </View>
              <Text style={styles.meta}>{t("ownerPanel.staffStore")}: {item.store_name || "—"}</Text>
              <View style={styles.pnRow}>
                <Ionicons name="search" size={14} color={colors.brand} />
                <Text style={styles.pnText}>{item.part_number_searched}</Text>
              </View>
              <View style={styles.pnRow}>
                <Ionicons name="location-outline" size={13} color={colors.info} />
                <Text style={styles.meta}>
                  {item.gps_coord ? `${item.gps_coord.lat.toFixed(5)}, ${item.gps_coord.lng.toFixed(5)}` : t("searchLogs.noLocation")}
                </Text>
              </View>
            </View>
          )}
          ListFooterComponent={
            logs.length < logsTotal ? (
              <Button
                title={loadingMoreLogs ? t("common.loading") : t("searchLogs.loadMore")}
                onPress={loadMoreLogs}
                variant="secondary"
                icon="chevron-down"
                loading={loadingMoreLogs}
                testID="owner-logs-load-more"
              />
            ) : null
          }
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
  tabBar: { flexDirection: "row", paddingHorizontal: spacing.lg, paddingTop: spacing.sm, gap: spacing.sm },
  tabBtn: { flex: 1, paddingVertical: spacing.sm, borderRadius: radius.sm, alignItems: "center", backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border },
  tabBtnActive: { backgroundColor: colors.brandFaint, borderColor: colors.brand },
  tabText: { color: colors.info, fontSize: font.sm, fontWeight: "700" },
  tabTextActive: { color: colors.brand },
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
  adminNote: { color: colors.success, fontSize: font.sm, fontWeight: "700" },
  otpBox: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.brand, borderRadius: radius.sm, padding: spacing.md, gap: 4, alignItems: "center" },
  otpLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "700", letterSpacing: 1 },
  otpValue: { color: colors.brand, fontSize: font.huge, fontWeight: "800", letterSpacing: 6 },
  otpHint: { color: colors.info, fontSize: font.sm - 1 },
  otpExpiry: { color: colors.info, fontSize: font.sm - 1 },
  pnRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  pnText: { color: colors.brand, fontSize: font.base, fontWeight: "800" },
  hint: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginBottom: spacing.sm },
  hintText: { color: colors.info, fontSize: font.sm - 1, flex: 1 },
});
