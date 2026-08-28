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
import { FilterChip } from "@/src/components/ui";
import { storage } from "@/src/utils/storage";
import { colors, font, radius, spacing } from "@/src/theme";

const COMPANIES = ["All", "Hyundai+Kia", "Maruti", "Tata", "Mahindra"];

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
  { key: "search", title: "SEARCH", gujarati: "શોધો", icon: "search", perm: "search", route: "/scan?mode=search", color: colors.info },
  { key: "buy", title: "BUY", gujarati: "ખરીદો", icon: "download", perm: "buy", route: "/scan?mode=buy", color: colors.success },
  { key: "sell", title: "SELL", gujarati: "વેચો", icon: "cash", perm: "sell", route: "/scan?mode=sell", color: colors.brand },
  { key: "requirement", title: "REQUIREMENT", gujarati: "જરૂરિયાત", icon: "add-circle", perm: "requirement", route: "/scan?mode=requirement", color: colors.warning },
  { key: "batch", title: "MULTIPLE BUY", gujarati: "ઝડપી બેચ ખરીદી", icon: "layers", perm: "buy", route: "/batch-buy", wide: true, color: colors.success },
];

export default function Home() {
  const { user, can } = useAuth();
  const { show } = useToast();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [company, setCompany] = useState("All");

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
      show("આ module માટે permission નથી", "error");
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const sep = m.route.includes("?") ? "&" : "?";
    router.push(`${m.route}${sep}company=${encodeURIComponent(company)}` as any);
  };

  return (
    <View style={styles.flex}>
      <View style={[styles.topBar, { paddingTop: insets.top + spacing.sm }]}>
        <View style={{ flex: 1 }}>
          <Text style={styles.hello}>સ્વાગત છે,</Text>
          <Text style={styles.name}>{user?.name}</Text>
        </View>
        <View style={styles.syncPill} testID="sync-pill">
          <View style={styles.syncDot} />
          <Text style={styles.syncText}>Online</Text>
        </View>
      </View>

      <ScrollView
        style={styles.flex}
        contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}
        showsVerticalScrollIndicator={false}
      >
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
            SEARCH, BUY, SELL કદી mix નહીં — દરેક અલગ module. Primary ID = Part Number.
          </Text>
        </View>

        <Text style={[styles.sectionLabel, { marginTop: spacing.xl }]}>REPORTS — રિપોર્ટ</Text>
        <View style={{ gap: spacing.md }}>
          {[
            { key: "buy", title: "ખરીદેલો માલ", sub: "બધી ખરીદી — તારીખ/company/category + Print", icon: "download" as const, color: colors.success },
            { key: "sell", title: "વેચેલો માલ", sub: "બધું વેચાણ — તારીખ/company/category + Print", icon: "cash" as const, color: colors.brand },
            { key: "stock", title: "Stock રિપોર્ટ", sub: "હાલનો સ્ટોક — company/category પ્રમાણે + Print", icon: "cube" as const, color: colors.info },
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
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  topBar: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
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
