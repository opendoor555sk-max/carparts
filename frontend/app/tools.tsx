import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";

import { useAuth } from "@/src/context/AuthContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Header } from "@/src/components/ui";
import { colors, font, radius, shadow, spacing } from "@/src/theme";
import type { TranslationKey } from "@/src/i18n/translations";

// The 7 destinations admin.tsx used to render as separate rows in its
// MANAGEMENT section (4 permission-gated via `links.map()`, 3 admin-only
// standalone Pressables) — consolidated here behind one "Tools" entry point.
// Same list-screen shape as (tabs)/reports.tsx (the established pattern for
// "several destinations, one overview list" in this app) rather than a
// dropdown/menu, which has no precedent anywhere in this codebase.
type ToolItem = {
  key: string;
  titleKey: TranslationKey;
  subKey: TranslationKey;
  icon: keyof typeof Ionicons.glyphMap;
  route: string;
  // Permission-gated items are fully hidden (not dimmed/locked) when the
  // viewer lacks the permission -- a staff account should only ever see
  // exactly what their assigned permissions cover. Items with no perm are
  // admin-only instead, same as their old standalone `{isAdmin ? ... : null}`
  // Pressables in admin.tsx before this list existed.
  perm?: string;
};

const TOOLS: ToolItem[] = [
  { key: "aiApprovals", titleKey: "admin.toolAiApprovals", subKey: "admin.linkAiApprovalsSub", icon: "sparkles", route: "/ai-approvals", perm: "ai_approve" },
  { key: "googleSearch", titleKey: "admin.toolSearchSetup", subKey: "admin.linkGoogleSearchSub", icon: "key", route: "/settings", perm: "search" },
  { key: "purchaseLimits", titleKey: "admin.toolBuyLimit", subKey: "admin.linkPurchaseLimitsSub", icon: "speedometer", route: "/limits", perm: "manage_limits" },
  { key: "demandSearch", titleKey: "admin.toolDemand", subKey: "admin.linkDemandSearchSub", icon: "trending-up", route: "/demand", perm: "view_stats" },
  { key: "stockVerify", titleKey: "admin.toolStockVerify", subKey: "admin.linkStockVerifySub", icon: "clipboard", route: "/stock-verify" },
  { key: "unlinkedStock", titleKey: "admin.toolUnlinked", subKey: "admin.linkUnlinkedStockSub", icon: "warning", route: "/unlinked-stock" },
  { key: "history", titleKey: "admin.toolPsHistory", subKey: "admin.toolBulkDelete", icon: "receipt", route: "/history" },
];

export default function Tools() {
  const router = useRouter();
  const { user, can } = useAuth();
  const { t } = useLanguage();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  const items = TOOLS.filter((it) => (it.perm ? can(it.perm) : isAdmin));

  return (
    <View style={styles.flex}>
      <Header title={t("admin.toolsTitle")} subtitle={t("admin.linkToolsSub")} onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
        {items.map((it) => (
          <Pressable
            key={it.key}
            style={styles.row}
            onPress={() => router.push(it.route as any)}
            testID={`tools-${it.key}`}
          >
            <View style={styles.icon}>
              <Ionicons name={it.icon} size={22} color={colors.brand} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.title}>{t(it.titleKey)}</Text>
              <Text style={styles.sub}>{t(it.subKey)}</Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={colors.info} />
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  row: {
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
  icon: {
    width: 44,
    height: 44,
    borderRadius: radius.sm,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.brandFaint,
    alignItems: "center",
    justifyContent: "center",
  },
  title: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  sub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
});
