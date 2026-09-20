import { useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Header, SignOutButton } from "@/src/components/ui";
import { brandingFromUser, shareDailySalesOnWhatsApp } from "@/src/utils/print";
import { colors, font, radius, shadow, spacing } from "@/src/theme";
import type { TranslationKey } from "@/src/i18n/translations";

// Simple launcher list — a "reports overview" tab that just links out to the
// existing report/list screens. There's no separate reports-data screen of
// its own: /report is the shared, mode-driven screen for buy/sell/stock,
// profit has its own dedicated screen, and Inventory/Requirements are the
// existing tabs (Requirements moved off the tab bar to make room for this
// one — see (tabs)/_layout.tsx — so it's still one tap away from here).
type LinkItem = {
  key: string;
  titleKey: TranslationKey;
  subKey: TranslationKey;
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
  route: string;
  // Profit/buy/sell reports read from /reports/profit and /transactions,
  // both require_admin server-side (a role check, not a per-permission
  // flag a staff account can be granted) -- so these are admin-only, same
  // as the "Share Daily Sales" row below. Stock report reads /inventory
  // and buy/sell modes' underlying data for inventory/requirements come
  // from endpoints any authenticated user can call, so those stay visible.
  adminOnly?: boolean;
};

const LINKS: LinkItem[] = [
  { key: "profit", titleKey: "profitReport.title", subKey: "home.reportProfitSub", icon: "trending-up", color: colors.warning, route: "/profit-report", adminOnly: true },
  { key: "stock", titleKey: "report.stockReport", subKey: "home.reportStockSub", icon: "cube", color: colors.info, route: "/report?mode=stock" },
  { key: "buy", titleKey: "report.purchases", subKey: "home.reportBuySub", icon: "download", color: colors.success, route: "/report?mode=buy", adminOnly: true },
  { key: "sell", titleKey: "report.sales", subKey: "home.reportSellSub", icon: "cash", color: colors.brand, route: "/report?mode=sell", adminOnly: true },
  { key: "inventory", titleKey: "tabs.inventory", subKey: "home.reportInventorySub", icon: "cube-outline", color: colors.brand, route: "/(tabs)/inventory" },
  { key: "requirements", titleKey: "tabs.needs", subKey: "home.reportRequirementsSub", icon: "list-circle", color: colors.warning, route: "/(tabs)/requirements" },
];

export default function Reports() {
  const router = useRouter();
  const { user } = useAuth();
  const { show } = useToast();
  const { t } = useLanguage();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";
  const [sharingSales, setSharingSales] = useState(false);
  const visibleLinks = LINKS.filter((l) => !l.adminOnly || isAdmin);

  // Not a navigation link like the rest of LINKS — it's an action (fetch
  // today's totals, then hand off to WhatsApp), so it's rendered separately
  // below rather than folded into LINKS.map's uniform router.push handler.
  const shareDailySales = async () => {
    setSharingSales(true);
    try {
      const today = new Date().toISOString().slice(0, 10);
      const report = await api.get<{ summary: any }>(`/reports/profit?date_from=${today}&date_to=${today}`);
      await shareDailySalesOnWhatsApp(await brandingFromUser(user), report.summary, new Date().toLocaleDateString());
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setSharingSales(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header
        title={t("tabs.reports")}
        subtitle={t("home.viewAllReportsSub")}
        right={<SignOutButton />}
      />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
        {visibleLinks.map((l) => (
          <Pressable
            key={l.key}
            style={styles.row}
            onPress={() => router.push(l.route as any)}
            testID={`reports-${l.key}`}
          >
            <View style={[styles.icon, { borderColor: l.color }]}>
              <Ionicons name={l.icon} size={22} color={l.color} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.title}>{t(l.titleKey)}</Text>
              <Text style={styles.sub}>{t(l.subKey)}</Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={colors.info} />
          </Pressable>
        ))}
        {isAdmin ? (
          <Pressable style={styles.row} onPress={shareDailySales} disabled={sharingSales} testID="reports-share-daily-sales">
            <View style={[styles.icon, { borderColor: "#25D366" }]}>
              <Ionicons name="logo-whatsapp" size={22} color="#25D366" />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.title}>{t("home.shareDailySales")}</Text>
              <Text style={styles.sub}>{t("home.shareDailySalesSub")}</Text>
            </View>
            {sharingSales ? <ActivityIndicator color={colors.brand} /> : <Ionicons name="chevron-forward" size={18} color={colors.info} />}
          </Pressable>
        ) : null}
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
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surface,
  },
  title: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  sub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
});
