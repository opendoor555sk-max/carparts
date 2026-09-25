import { useCallback, useState } from "react";
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Header, Loading, FilterChip } from "@/src/components/ui";
import { LANGUAGES } from "@/src/i18n/translations";
import { OWNER_CONTACT } from "@/src/constants/owner";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

type Stats = {
  total_parts: number;
  in_stock_units: number;
  sold_units: number;
  pending_requirements: number;
  pending_ai: number;
  verified_parts: number;
  unverified_parts: number;
  known_parts: number;
  total_buys: number;
  total_sells: number;
};

export default function Admin() {
  const { user, logout, can } = useAuth();
  const { language, setLanguage, t } = useLanguage();
  const router = useRouter();
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      if (can("view_stats")) setStats(await api.get<Stats>("/stats"));
    } catch {
    } finally {
      setLoading(false);
    }
  }, [can]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const isAdmin = user?.role === "admin";
  const isSuperAdmin = user?.role === "super_admin";
  // Platform Owner is a single hardcoded contact, not a role — see
  // is_owner() in server.py. This is UI visibility only; the real
  // authorization for every /owner/* call is re-checked server-side.
  const isOwner = !!user?.contact && user.contact === OWNER_CONTACT;

  // "sold" links to /report?mode=sell, which reads /transactions --
  // require_admin server-side (a role check, not a grantable permission
  // flag), so it's excluded for non-admin staff even when they hold
  // view_stats. The rest read from endpoints any authenticated user can
  // call (/inventory, /requirements) or stay view-only for non-approvers
  // (/ai-approvals -- its Approve/Reject actions are separately gated by
  // ai_approve inside that screen), so they stay visible under view_stats
  // alone.
  const canViewSalesReport = isAdmin || isSuperAdmin;
  const statCards: { key: string; label: string; value: number; color: string; icon: keyof typeof Ionicons.glyphMap; route: string }[] = stats
    ? [
        { key: "parts", label: t("admin.statParts"), value: stats.total_parts, color: colors.brand, icon: "documents", route: "/report?mode=stock" },
        { key: "inStock", label: t("admin.statInStock"), value: stats.in_stock_units, color: colors.success, icon: "cube", route: "/report?mode=stock" },
        ...(canViewSalesReport
          ? [{ key: "sold", label: t("admin.statSold"), value: stats.sold_units, color: colors.info, icon: "cash" as const, route: "/report?mode=sell" }]
          : []),
        { key: "pendingNeeds", label: t("admin.statPendingNeeds"), value: stats.pending_requirements, color: colors.warning, icon: "list", route: "/(tabs)/requirements" },
        { key: "aiPending", label: t("admin.statAiPending"), value: stats.pending_ai, color: colors.warning, icon: "sparkles", route: "/ai-approvals" },
        { key: "verified", label: t("admin.statVerified"), value: stats.verified_parts, color: colors.success, icon: "shield-checkmark", route: "/report?mode=stock" },
      ]
    : [];

  // AI Approvals / Google Search Setup / Purchase Limits / Demand & Search /
  // Stock Verification / Unlinked Stock / Purchase-Sale History (+ its bulk
  // delete) used to be 7 separate rows here, then a single consolidated
  // "Tools" row (tools.tsx). That Tools row has since moved to Home's
  // MODULES grid instead — see (tabs)/index.tsx — so it's gone from here
  // entirely now. Manage Users stays a standalone row; it was never part of
  // that consolidation.
  const links: { key: string; title: string; sub: string; icon: keyof typeof Ionicons.glyphMap; route: string; perm: string }[] = [
    { key: "manageUsers", title: t("users.title"), sub: t("admin.linkManageUsersSub"), icon: "people", route: "/users", perm: "manage_users" },
  ];

  return (
    <View style={styles.flex}>
      <Header
        title={t("admin.title")}
        subtitle={user?.store_name ? `${user?.name} · ${user?.store_name}` : user?.name}
        right={
          // TEMP: icon + "(TEST)" label swapped for visual OTA-delivery
          // verification — see the OTA debug panel on the login screen.
          // Revert to the plain log-out-outline icon once confirmed.
          <Pressable onPress={logout} hitSlop={12} testID="logout-btn" style={styles.logoutBtn}>
            <Ionicons name="flask" size={24} color={colors.error} />
            <Text style={styles.logoutTestLabel}>(TEST)</Text>
          </Pressable>
        }
      />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}>
        {loading ? (
          <Loading />
        ) : (
          <>
            {stats ? (
              <>
                <Text style={styles.section}>{t("admin.statistics").toUpperCase()}</Text>
                <View style={styles.statGrid}>
                  {statCards.map((s) => (
                    <Pressable key={s.key} style={styles.statCard} onPress={() => router.push(s.route as any)} testID={`stat-${s.key}`}>
                      <Ionicons name={s.icon} size={20} color={s.color} />
                      <Text style={[styles.statValue, { color: s.color }]}>{s.value}</Text>
                      <Text style={styles.statLabel}>{s.label}</Text>
                    </Pressable>
                  ))}
                </View>
              </>
            ) : null}

            <Text style={[styles.section, { marginTop: spacing.xl }]}>{t("admin.management").toUpperCase()}</Text>
            {isSuperAdmin ? (
              <Pressable
                style={[styles.link, { marginBottom: spacing.md, borderColor: colors.brand }]}
                onPress={() => router.push("/stores" as any)}
                testID="admin-link-stores"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="business" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("stores.title")}</Text>
                  <Text style={styles.linkSub}>{t("admin.linkAllStoresSub")}</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : null}
            {isSuperAdmin ? (
              <Pressable
                style={[styles.link, { marginBottom: spacing.md, borderColor: colors.success }]}
                onPress={() => router.push("/admin-gps" as any)}
                testID="admin-link-gps"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="location" size={22} color={colors.success} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("admin.linkGpsTitle")}</Text>
                  <Text style={styles.linkSub}>{t("admin.linkGpsSub")}</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : null}
            {isOwner ? (
              <Pressable
                style={[styles.link, { marginBottom: spacing.md, borderColor: colors.error }]}
                onPress={() => router.push("/owner-panel" as any)}
                testID="admin-link-owner-panel"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="planet" size={22} color={colors.error} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("ownerPanel.title")}</Text>
                  <Text style={styles.linkSub}>{t("admin.linkOwnerPanelSub")}</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : null}
            {isAdmin || isSuperAdmin ? (
              <Pressable
                style={[styles.link, { marginBottom: spacing.md, borderColor: colors.info }]}
                onPress={() =>
                  Linking.openURL(
                    "https://github.com/opendoor555sk-max/carparts/blob/conflict_040926_0622/PROJECT_BLUEPRINT.md",
                  )
                }
                testID="admin-link-blueprint"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="document-text" size={22} color={colors.info} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("admin.linkBlueprintTitle")}</Text>
                  <Text style={styles.linkSub}>{t("admin.linkBlueprintSub")}</Text>
                </View>
                <Ionicons name="open-outline" size={18} color={colors.info} />
              </Pressable>
            ) : null}
            <View style={{ gap: spacing.md }}>
              {links.filter((l) => can(l.perm)).map((l) => (
                <Pressable
                  key={l.key}
                  style={styles.link}
                  onPress={() => router.push(l.route as any)}
                  testID={`admin-link-${l.key}`}
                >
                  <View style={styles.linkIcon}>
                    <Ionicons name={l.icon} size={22} color={colors.brand} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.linkTitle}>{l.title}</Text>
                    <Text style={styles.linkSub}>{l.sub}</Text>
                  </View>
                  <Ionicons name="chevron-forward" size={18} color={colors.info} />
                </Pressable>
              ))}
            </View>

            {isAdmin ? (
              <Pressable
                style={[styles.link, { marginTop: spacing.md }]}
                onPress={() => router.push("/backup" as any)}
                testID="admin-link-backup"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="cloud-download" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("backup.title")}</Text>
                  <Text style={styles.linkSub}>{t("admin.linkBackupSub")}</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : null}

            <View style={styles.note} testID="admin-location-notice">
              <Ionicons name="location" size={16} color={colors.info} />
              <Text style={styles.noteText}>{t("admin.locationNotice")}</Text>
            </View>

            <Text style={[styles.section, { marginTop: spacing.xl }]}>{t("admin.account").toUpperCase()}</Text>
            <View style={[styles.link, { flexDirection: "column", alignItems: "stretch", marginBottom: spacing.md }]} testID="admin-language">
              <View style={{ flexDirection: "row", alignItems: "center", gap: spacing.md, marginBottom: spacing.md }}>
                <View style={styles.linkIcon}>
                  <Ionicons name="language" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("admin.language")}</Text>
                  <Text style={styles.linkSub}>{t("admin.languageSub")}</Text>
                </View>
              </View>
              <View style={{ flexDirection: "row", gap: spacing.sm, flexWrap: "wrap" }}>
                {LANGUAGES.map((l) => (
                  <FilterChip
                    key={l.code}
                    label={l.label}
                    active={language === l.code}
                    onPress={() => setLanguage(l.code)}
                    testID={`admin-language-${l.code}`}
                  />
                ))}
              </View>
            </View>
            {isAdmin ? (
              <Pressable
                style={[styles.link, { marginBottom: spacing.md }]}
                onPress={() => router.push("/store-profile" as any)}
                testID="admin-link-store-profile"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="storefront" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>{t("admin.linkStoreProfileTitle")}</Text>
                  <Text style={styles.linkSub}>{t("admin.linkStoreProfileSub")}</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : null}
            <Pressable
              style={styles.link}
              onPress={() => router.push("/change-password" as any)}
              testID="admin-link-change-password"
            >
              <View style={styles.linkIcon}>
                <Ionicons name="key" size={22} color={colors.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.linkTitle}>{t("admin.linkMyPasswordTitle")}</Text>
                <Text style={styles.linkSub}>{t("admin.linkMyPasswordSub")}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.info} />
            </Pressable>

            {!isAdmin ? (
              <View style={styles.note}>
                <Ionicons name="information-circle" size={16} color={colors.info} />
                <Text style={styles.noteText}>{t("admin.staffNote")}</Text>
              </View>
            ) : null}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  logoutBtn: { flexDirection: "row", alignItems: "center", gap: 2 },
  logoutTestLabel: { color: colors.error, fontSize: font.sm - 2, fontWeight: "800" },
  section: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  statGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md },
  statCard: {
    width: "31%",
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.md,
    gap: spacing.xs,
    ...shadow.sm,
  },
  statValue: { fontSize: font.xxl, fontWeight: "800" },
  statLabel: { color: colors.info, fontSize: font.sm - 1 },
  link: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
    ...shadow.sm,
  },
  linkIcon: {
    width: 44,
    height: 44,
    borderRadius: radius.sm,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.brandFaint,
    alignItems: "center",
    justifyContent: "center",
  },
  linkTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  linkSub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  note: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.xl, alignItems: "center" },
  noteText: { color: colors.info, fontSize: font.sm, flex: 1 },
});
