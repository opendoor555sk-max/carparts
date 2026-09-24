import { useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { SignOutButton } from "@/src/components/ui";
import { useLowStockCount } from "@/src/hooks/use-low-stock-count";
import { brandingFromUser, shareLowStockOnWhatsApp, type LowStockRow } from "@/src/utils/print";
import { colors, font, radius, shadow, spacing } from "@/src/theme";
import type { TranslationKey } from "@/src/i18n/translations";


type Module = {
  key: string;
  title: string;
  gujarati: string;
  icon: keyof typeof Ionicons.glyphMap;
  // Omit for a tile with no permission requirement of its own (e.g. Tools,
  // which had none as an Admin Panel row either — access there depended only
  // on reaching the Admin tab, which every logged-in user can).
  perm?: string;
  route: string;
  wide?: boolean;
  color: string;
  // Only for tiles that must stay hidden from every store's own admin/staff,
  // no matter their permissions -- Stock Transfer touches TWO stores' stock
  // in one call, so it's restricted to the platform owner (super_admin), the
  // only role that can even see more than one store.
  superAdminOnly?: boolean;
  // Visible to a store's own admin AND the platform owner, but never to
  // staff regardless of their assigned permissions -- the audit trail is
  // meant to watch staff actions, so staff themselves shouldn't be the ones
  // who can browse or (via checking it) sanity-test it away.
  adminOnly?: boolean;
};

const MODULES: Module[] = [
  { key: "search", title: "SEARCH", gujarati: "Find part", icon: "search", perm: "search", route: "/scan?mode=search", color: colors.info },
  // Single "BUY" module (formerly split into "BUY" -> scan.tsx -> single-item
  // form, and a separate "MULTIPLE BUY" fast-scan screen) -- buy.tsx now does
  // both: instant scan-to-draft like the old batch screen, with every single-
  // item field (condition/photos/price/print/catalog-autofill/override)
  // available per line. Routes straight there; no scan.tsx detour needed since
  // buy.tsx has its own built-in camera scanning.
  { key: "buy", title: "BUY", gujarati: "Purchase", icon: "download", perm: "buy", route: "/buy", color: colors.success },
  { key: "sell", title: "SELL", gujarati: "Sale", icon: "cash", perm: "sell", route: "/scan?mode=sell", color: colors.brand },
  { key: "requirement", title: "REQUIREMENT", gujarati: "Inquiry / Need", icon: "add-circle", perm: "requirement", route: "/scan?mode=requirement", color: colors.warning },
  { key: "customers", title: "CUSTOMERS", gujarati: "Grahak Khata", icon: "people", perm: "sell", route: "/customers", color: colors.brand },
  { key: "vendors", title: "VENDORS", gujarati: "Supplier records", icon: "briefcase", perm: "buy", route: "/vendors", color: colors.success },
  { key: "damaged-returns", title: "DAMAGED / RETURNS", gujarati: "Returns & spoilage", icon: "return-up-back", perm: "sell", route: "/damaged-returns", color: colors.error },
  { key: "cash-book", title: "CASH BOOK", gujarati: "Rokad no hisab", icon: "wallet", perm: "sell", route: "/cash-book", color: colors.warning },
  { key: "purchase-orders", title: "PURCHASE ORDERS", gujarati: "Vendor ne order aapo", icon: "clipboard", perm: "buy", route: "/purchase-orders", color: colors.success },
  { key: "quotations", title: "QUOTATIONS", gujarati: "Grahak ne bhav aapo", icon: "document-text", perm: "sell", route: "/quotations", color: colors.brand },
  { key: "stock-transfer", title: "STOCK TRANSFER", gujarati: "Store thi store stock mokalo", icon: "swap-horizontal", route: "/stock-transfer", color: colors.info, superAdminOnly: true },
  { key: "reservations", title: "STOCK HOLD", gujarati: "Grahak mate stock rakho", icon: "lock-closed", perm: "sell", route: "/reservations", color: colors.warning },
  { key: "audit-log", title: "ACTIVITY LOG", gujarati: "Kone shu badalyu", icon: "time", route: "/audit-log", color: colors.info, adminOnly: true },
  // Moved from Admin Panel's Management section (was a single unconditional
  // row there, gated only by reaching the Admin tab at all) — no perm here
  // either, for the same reason. The 7 items inside /tools keep their own
  // per-item permission/admin gating unchanged.
  { key: "tools", title: "TOOLS", gujarati: "Admin utilities", icon: "construct", route: "/tools", color: colors.brand },
  { key: "arrange", title: "STORE ARRANGEMENT", gujarati: "Place bought stock", icon: "location", perm: "buy", route: "/store-arrangement", wide: true, color: colors.info },
];

export default function Home() {
  const { user, can } = useAuth();
  const { show } = useToast();
  const { t } = useLanguage();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  // Vehicle-company filter used to be a row of chips at the top of Home
  // (removed at the owner's request — the "COMPANY GATE" label read as
  // broken/nonsensical translated text and the whole row was more clutter
  // than it was worth). `company` stays fixed at "All" so every module
  // route below still gets its usual ?company= param -- unfiltered, exactly
  // like picking "All" always did -- without touching buy/sell/search,
  // which all read that param.
  const company = "All";
  const [sharingLowStock, setSharingLowStock] = useState(false);
  const lowStockCount = useLowStockCount();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  // Tiles the caller has no permission for are filtered out entirely below
  // (visibleModules) rather than rendered dimmed/locked — a staff account
  // should only ever see the modules their assigned permissions cover.
  const openModule = (m: Module) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const sep = m.route.includes("?") ? "&" : "?";
    router.push(`${m.route}${sep}company=${encodeURIComponent(company)}` as any);
  };

  const visibleModules = MODULES.filter(
    (m) =>
      (!m.perm || can(m.perm)) &&
      (!m.superAdminOnly || user?.role === "super_admin") &&
      (!m.adminOnly || isAdmin),
  );

  const shareLowStock = async () => {
    setSharingLowStock(true);
    try {
      const rows = await api.get<LowStockRow[]>("/inventory/low-stock");
      if (!rows.length) {
        show(t("home.noLowStock"), "info");
        return;
      }
      await shareLowStockOnWhatsApp(await brandingFromUser(user), rows);
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setSharingLowStock(false);
    }
  };

  return (
    <View style={styles.flex}>
      <View style={[styles.topBarWrap, { paddingTop: insets.top + spacing.sm }]}>
        <View style={styles.topBar}>
          <View style={{ flex: 1 }}>
            {/* TEMP: small, easy-to-spot marker for re-verifying OTA delivery
                — bump the label (OTA v2 -> v3 -> ...) each time we need fresh
                proof an update actually reached a device. Remove once OTA
                delivery is confirmed working reliably. */}
            <Text style={styles.hello}>
              Welcome, <Text style={styles.otaMarker}>[OTA v4]</Text>
            </Text>
            <Text style={styles.name}>{user?.name}</Text>
          </View>
          <View style={styles.syncPill} testID="sync-pill">
            <View style={styles.syncDot} />
            <Text style={styles.syncText}>Online</Text>
          </View>
        </View>
        <View style={styles.signOutRow}>
          <SignOutButton />
        </View>
      </View>

      <ScrollView
        style={styles.flex}
        contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}
        showsVerticalScrollIndicator={false}
      >
        {lowStockCount > 0 ? (
          <View style={styles.lowStockBanner}>
            <Pressable
              style={styles.lowStockMain}
              onPress={() => router.push("/(tabs)/inventory" as any)}
              testID="home-low-stock-banner"
            >
              <Ionicons name="alert-circle" size={20} color={colors.onError} />
              <Text style={styles.lowStockText}>
                {lowStockCount} part{lowStockCount === 1 ? "" : "s"} low on stock — tap to view
              </Text>
            </Pressable>
            {sharingLowStock ? (
              <ActivityIndicator color={colors.onError} />
            ) : (
              <Pressable onPress={shareLowStock} hitSlop={10} testID="home-low-stock-whatsapp">
                <Ionicons name="logo-whatsapp" size={20} color={colors.onError} />
              </Pressable>
            )}
            <Pressable onPress={() => router.push("/(tabs)/inventory" as any)} hitSlop={10} testID="home-low-stock-chevron">
              <Ionicons name="chevron-forward" size={18} color={colors.onError} />
            </Pressable>
          </View>
        ) : null}

        <Text style={styles.sectionLabel}>{t("home.modules").toUpperCase()}</Text>
        <View style={styles.grid}>
          {visibleModules.map((m) => (
            <Pressable
              key={m.key}
              onPress={() => openModule(m)}
              testID={`module-${m.key}`}
              style={[styles.tile, m.wide && styles.tileWide]}
            >
              <View style={[styles.tileIcon, { borderColor: m.color }]}>
                <Ionicons name={m.icon} size={26} color={m.color} />
              </View>
              <View>
                <Text style={styles.tileTitle}>{t(`module.${m.key}` as TranslationKey)}</Text>
                <Text style={styles.tileGuj}>{m.gujarati}</Text>
              </View>
            </Pressable>
          ))}
        </View>

        <View style={styles.hintBox}>
          <Ionicons name="information-circle" size={18} color={colors.brand} />
          <Text style={styles.hintText}>{t("home.hint")}</Text>
        </View>

        {isAdmin ? (
          <>
            <Text style={[styles.sectionLabel, { marginTop: spacing.xl }]}>STICKER PRINTING</Text>
            <View style={{ gap: spacing.md }}>
              <Pressable
                style={styles.report}
                onPress={() => router.push("/scan-sticker" as any)}
                testID="home-scan-sticker"
              >
                <View style={[styles.reportIcon, { borderColor: colors.brand }]}>
                  <Ionicons name="scan" size={22} color={colors.brand} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.reportTitle}>AI Sticker Scanner</Text>
                  <Text style={styles.reportSub}>Scan any sticker from gallery/camera • edit part no • reprint</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            </View>
          </>
        ) : null}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  topBarWrap: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  topBar: {
    flexDirection: "row",
    alignItems: "center",
  },
  signOutRow: { alignItems: "flex-end", marginTop: spacing.sm },
  hello: { color: colors.info, fontSize: font.sm },
  otaMarker: { color: colors.brand, fontWeight: "800" },
  name: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800" },
  syncPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
  },
  syncDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.success },
  syncText: { color: colors.onSurface2, fontSize: font.sm, fontWeight: "700" },
  sectionLabel: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  lowStockBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    backgroundColor: colors.error,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.lg,
  },
  lowStockMain: { flex: 1, flexDirection: "row", alignItems: "center", gap: spacing.sm },
  lowStockText: { flex: 1, color: colors.onError, fontWeight: "800", fontSize: font.sm },
  chipRow: { gap: spacing.sm, paddingRight: spacing.lg },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md },
  tile: {
    width: "48%",
    minHeight: 118,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
    justifyContent: "space-between",
    gap: spacing.md,
    ...shadow.sm,
  },
  tileWide: { width: "100%", flexDirection: "row", alignItems: "center", minHeight: 88 },
  tileIcon: {
    width: 48,
    height: 48,
    borderRadius: radius.md,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surface,
  },
  tileTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  tileGuj: { color: colors.info, fontSize: font.base, marginTop: 2 },
  hintBox: {
    flexDirection: "row",
    gap: spacing.sm,
    backgroundColor: colors.brandFaint,
    borderRadius: radius.md,
    padding: spacing.md,
    marginTop: spacing.xl,
  },
  hintText: { color: colors.onBrandFaint, fontSize: font.sm, flex: 1, lineHeight: 18 },
  report: {
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
  reportIcon: {
    width: 44,
    height: 44,
    borderRadius: radius.sm,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surface,
  },
  reportTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  reportSub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
});
