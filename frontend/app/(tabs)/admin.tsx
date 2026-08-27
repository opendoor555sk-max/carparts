import { useCallback, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

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

  const statCards: { label: string; value: number; color: string; icon: keyof typeof Ionicons.glyphMap }[] = stats
    ? [
        { label: "Parts", value: stats.total_parts, color: colors.brand, icon: "documents" },
        { label: "In Stock", value: stats.in_stock_units, color: colors.success, icon: "cube" },
        { label: "Sold", value: stats.sold_units, color: colors.info, icon: "cash" },
        { label: "Pending Needs", value: stats.pending_requirements, color: colors.warning, icon: "list" },
        { label: "AI Pending", value: stats.pending_ai, color: colors.warning, icon: "sparkles" },
        { label: "Verified", value: stats.verified_parts, color: colors.success, icon: "shield-checkmark" },
      ]
    : [];

  const links: { title: string; sub: string; icon: keyof typeof Ionicons.glyphMap; route: string; perm: string }[] = [
    { title: "AI Approvals", sub: "Gemini research pending approval", icon: "sparkles", route: "/ai-approvals", perm: "ai_approve" },
    { title: "Google Search Setup", sub: "Your own API key — free 100/day", icon: "key", route: "/settings", perm: "search" },
    { title: "Purchase Limits", sub: "Global + per-part limits", icon: "speedometer", route: "/limits", perm: "manage_limits" },
    { title: "Manage Users", sub: "બધા users નો username + password બદલો", icon: "people", route: "/users", perm: "manage_users" },
    { title: "Demand & Search", sub: "High-demand detection", icon: "trending-up", route: "/demand", perm: "view_stats" },
  ];

  return (
    <View style={styles.flex}>
      <Header
        title="Admin Panel"
        subtitle={user?.name}
        right={
          <Pressable onPress={logout} hitSlop={12} testID="logout-btn">
            <Ionicons name="log-out-outline" size={24} color={colors.error} />
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
                <Text style={styles.section}>STATISTICS</Text>
                <View style={styles.statGrid}>
                  {statCards.map((s) => (
                    <View key={s.label} style={styles.statCard} testID={`stat-${s.label}`}>
                      <Ionicons name={s.icon} size={20} color={s.color} />
                      <Text style={[styles.statValue, { color: s.color }]}>{s.value}</Text>
                      <Text style={styles.statLabel}>{s.label}</Text>
                    </View>
                  ))}
                </View>
              </>
            ) : null}

            <Text style={[styles.section, { marginTop: spacing.xl }]}>MANAGEMENT</Text>
            <View style={{ gap: spacing.md }}>
              {links.map((l) => {
                const allowed = can(l.perm);
                return (
                  <Pressable
                    key={l.title}
                    style={[styles.link, !allowed && { opacity: 0.45 }]}
                    onPress={() => (allowed ? router.push(l.route as any) : null)}
                    testID={`admin-link-${l.title}`}
                  >
                    <View style={styles.linkIcon}>
                      <Ionicons name={l.icon} size={22} color={colors.brand} />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.linkTitle}>{l.title}</Text>
                      <Text style={styles.linkSub}>{l.sub}</Text>
                    </View>
                    <Ionicons name={allowed ? "chevron-forward" : "lock-closed"} size={18} color={colors.info} />
                  </Pressable>
                );
              })}
            </View>

            {isAdmin ? (
              <Pressable
                style={[styles.link, { marginTop: spacing.md }]}
                onPress={() => router.push("/stock-verify" as any)}
                testID="admin-link-stock-verify"
              >
                <View style={styles.linkIcon}>
                  <Ionicons name="clipboard" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.linkTitle}>Stock Verification</Text>
                  <Text style={styles.linkSub}>ફિઝિકલ સ્ટોક ગણી ગાયબ part શોધો</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : null}

            <Text style={[styles.section, { marginTop: spacing.xl }]}>ACCOUNT</Text>
            <Pressable
              style={styles.link}
              onPress={() => router.push("/change-password" as any)}
              testID="admin-link-change-password"
            >
              <View style={styles.linkIcon}>
                <Ionicons name="key" size={22} color={colors.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.linkTitle}>મારો પોતાનો Password</Text>
                <Text style={styles.linkSub}>ફક્ત તમારો login password બદલો</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.info} />
            </Pressable>

            {!isAdmin ? (
              <View style={styles.note}>
                <Ionicons name="information-circle" size={16} color={colors.info} />
                <Text style={styles.noteText}>તમે staff છો — કેટલાક admin controls locked છે.</Text>
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
