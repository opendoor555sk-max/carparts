import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";

import { useLanguage } from "@/src/context/LanguageContext";
import { Header, SignOutButton } from "@/src/components/ui";
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
};

const LINKS: LinkItem[] = [
  { key: "profit", titleKey: "profitReport.title", subKey: "home.reportProfitSub", icon: "trending-up", color: colors.warning, route: "/profit-report" },
  { key: "stock", titleKey: "report.stockReport", subKey: "home.reportStockSub", icon: "cube", color: colors.info, route: "/report?mode=stock" },
  { key: "buy", titleKey: "report.purchases", subKey: "home.reportBuySub", icon: "download", color: colors.success, route: "/report?mode=buy" },
  { key: "sell", titleKey: "report.sales", subKey: "home.reportSellSub", icon: "cash", color: colors.brand, route: "/report?mode=sell" },
  { key: "inventory", titleKey: "tabs.inventory", subKey: "home.reportInventorySub", icon: "cube-outline", color: colors.brand, route: "/(tabs)/inventory" },
  { key: "requirements", titleKey: "tabs.needs", subKey: "home.reportRequirementsSub", icon: "list-circle", color: colors.warning, route: "/(tabs)/requirements" },
];

export default function Reports() {
  const router = useRouter();
  const { t } = useLanguage();

  return (
    <View style={styles.flex}>
      <Header
        title={t("tabs.reports")}
        subtitle={t("home.viewAllReportsSub")}
        right={<SignOutButton />}
      />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
        {LINKS.map((l) => (
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
