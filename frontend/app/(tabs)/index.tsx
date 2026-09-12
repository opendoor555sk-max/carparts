import { useEffect, useState } from "react";
import {
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

import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { FilterChip, SignOutButton } from "@/src/components/ui";
import { storage } from "@/src/utils/storage";
import { useLowStockCount } from "@/src/hooks/use-low-stock-count";
import { colors, font, radius, spacing } from "@/src/theme";

const COMPANIES = ["All", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda", "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet"];

type Module = {
  key: string;
  title: string;
  gujarati: string;
  icon: keyof typeof Ionicons.glyphMap;
  perm: string;
  route: string;
  wide?: boolean;
  color: string;
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
  { key: "arrange", title: "STORE ARRANGEMENT", gujarati: "Place bought stock", icon: "location", perm: "buy", route: "/store-arrangement", wide: true, color: colors.info },
];

export default function Home() {
  const { user, can } = useAuth();
  const { show } = useToast();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [company, setCompany] = useState("All");
  const lowStockCount = useLowStockCount();

  useEffect(() => {
    (async () => {
      const saved = await storage.getItem<string>("kabadi.company", "All");
      if (saved) setCompany(saved);
    })();
  }, []);

  const selectCompany = async (c: string) => {
    setCompany(c);
    await storage.setItem("kabadi.company", c);
    Haptics.selectionAsync();
  };

  const openModule = (m: Module) => {
    if (!can(m.perm)) {
      show("No permission for this module", "error");
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const sep = m.route.includes("?") ? "&" : "?";
    router.push(`${m.route}${sep}company=${encodeURIComponent(company)}` as any);
  };

  return (
    <View style={styles.flex}>
      <View style={[styles.topBarWrap, { paddingTop: insets.top + spacing.sm }]}>
        <View style={styles.topBar}>
          <View style={{ flex: 1 }}>
            <Text style={styles.hello}>Welcome,</Text>
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
          <Pressable
            style={styles.lowStockBanner}
            onPress={() => router.push("/(tabs)/inventory" as any)}
            testID="home-low-stock-banner"
          >
            <Ionicons name="alert-circle" size={20} color={colors.onError} />
            <Text style={styles.lowStockText}>
              {lowStockCount} part{lowStockCount === 1 ? "" : "s"} low on stock — tap to view
            </Text>
            <Ionicons name="chevron-forward" size={18} color={colors.onError} />
          </Pressable>
        ) : null}

        <Text style={styles.sectionLabel}>COMPANY GATE</Text>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.chipRow}
          style={{ marginBottom: spacing.lg }}
        >
          {COMPANIES.map((c) => (
            <FilterChip
              key={c}
              label={c}
              active={company === c}
              onPress={() => selectCompany(c)}
              testID={`company-${c}`}
            />
          ))}
        </ScrollView>

        <Text style={styles.sectionLabel}>MODULES</Text>
        <View style={styles.grid}>
          {MODULES.map((m) => {
            const allowed = can(m.perm);
            return (
              <Pressable
                key={m.key}
                onPress={() => openModule(m)}
                testID={`module-${m.key}`}
                style={[styles.tile, m.wide && styles.tileWide, !allowed && { opacity: 0.45 }]}
              >
                <View style={[styles.tileIcon, { borderColor: m.color }]}>
                  <Ionicons name={m.icon} size={26} color={m.color} />
                </View>
                <View>
                  <Text style={styles.tileTitle}>{m.title}</Text>
                  <Text style={styles.tileGuj}>{m.gujarati}</Text>
                </View>
                {!allowed ? (
                  <Ionicons name="lock-closed" size={14} color={colors.info} style={styles.lock} />
                ) : null}
              </Pressable>
            );
          })}
        </View>

        <View style={styles.hintBox}>
          <Ionicons name="information-circle" size={18} color={colors.brand} />
          <Text style={styles.hintText}>
            SEARCH, BUY, SELL are never mixed — each is a separate module. Primary ID = Part Number.
          </Text>
        </View>

        <Text style={[styles.sectionLabel, { marginTop: spacing.xl }]}>REPORTS</Text>
        <View style={{ gap: spacing.md }}>
          {[
            { key: "buy", title: "Purchases", sub: "All buys — date / company / category + Print", icon: "download" as const, color: colors.success },
            { key: "sell", title: "Sales", sub: "All sales — date / company / category + Print", icon: "cash" as const, color: colors.brand },
            { key: "stock", title: "Stock Report", sub: "Current stock by company / category + Print", icon: "cube" as const, color: colors.info },
          ].map((r) => (
            <Pressable
              key={r.key}
              style={styles.report}
              onPress={() => router.push(`/report?mode=${r.key}` as any)}
              testID={`report-${r.key}`}
            >
              <View style={[styles.reportIcon, { borderColor: r.color }]}>
                <Ionicons name={r.icon} size={22} color={r.color} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.reportTitle}>{r.title}</Text>
                <Text style={styles.reportSub}>{r.sub}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.info} />
            </Pressable>
          ))}
        </View>

        {user?.role === "super_admin" ? (
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
  signOutRow: { alignItems: "center", marginTop: spacing.sm },
  hello: { color: colors.info, fontSize: font.sm },
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
    gap: spacing.sm,
    backgroundColor: colors.error,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.lg,
  },
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
  lock: { position: "absolute", top: spacing.md, right: spacing.md },
  hintBox: {
    flexDirection: "row",
    gap: spacing.sm,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.md,
    marginTop: spacing.xl,
  },
  hintText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 18 },
  report: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
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
