import { useCallback, useMemo, useState } from "react";
import { FlatList, Linking, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Header, Loading } from "@/src/components/ui";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

type GpsPoint = {
  type: "Requirement" | "Purchase";
  part_number: string;
  store_id: string;
  store_name: string;
  by: string;
  at: string;
  gps: string;
  lat: number;
  lng: number;
};

type DevicePoint = {
  user_id: string;
  name: string;
  username: string;
  store_id: string | null;
  store_name: string;
  lat: number;
  lng: number;
  at: string;
};

const FILTERS = ["All", "Requirement", "Purchase"] as const;
const MODES = ["activity", "live"] as const;

export default function AdminGps() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const filterLabel: Record<(typeof FILTERS)[number], string> = {
    All: t("common.all"),
    Requirement: t("module.requirement"),
    Purchase: t("adminGps.purchase"),
  };
  const [mode, setMode] = useState<(typeof MODES)[number]>("activity");
  const [points, setPoints] = useState<GpsPoint[]>([]);
  const [devicePoints, setDevicePoints] = useState<DevicePoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("All");

  const load = useCallback(async () => {
    try {
      if (mode === "live") setDevicePoints(await api.get<DevicePoint[]>("/owner/device-locations"));
      else setPoints(await api.get<GpsPoint[]>("/admin/gps-locations"));
    } catch (e: any) {
      show(e?.message || t("common.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [mode, show, t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const data = useMemo(
    () => (filter === "All" ? points : points.filter((p) => p.type === filter)),
    [points, filter],
  );

  const fmtAgo = (iso: string) => {
    const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
    if (mins < 1) return t("adminGps.justNow");
    if (mins < 60) return `${mins} ${t("adminGps.minsAgo")}`;
    const hrs = Math.round(mins / 60);
    if (hrs < 24) return `${hrs} ${t("adminGps.hoursAgo")}`;
    return new Date(iso).toLocaleDateString();
  };

  const openMapCoord = (lat: number, lng: number, label: string) => {
    const enc = encodeURIComponent(label);
    const url = Platform.select({
      ios: `https://maps.google.com/?q=${lat},${lng}(${enc})`,
      android: `https://maps.google.com/?q=${lat},${lng}(${enc})`,
      default: `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`,
    })!;
    Linking.openURL(url).catch(() => show(t("adminGps.couldNotOpenMaps"), "error"));
  };

  const openMap = (p: GpsPoint) => {
    const label = encodeURIComponent(`${p.part_number} (${p.store_name})`);
    const url = Platform.select({
      ios: `https://maps.google.com/?q=${p.lat},${p.lng}(${label})`,
      android: `https://maps.google.com/?q=${p.lat},${p.lng}(${label})`,
      default: `https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lng}`,
    })!;
    Linking.openURL(url).catch(() => show(t("adminGps.couldNotOpenMaps"), "error"));
  };

  const fmt = (iso: string) => {
    try {
      const d = new Date(iso);
      return d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    } catch {
      return iso;
    }
  };

  return (
    <View style={styles.flex}>
      <Header title={t("adminGps.title")} subtitle={t("adminGps.subtitle")} onBack={() => router.back()} />

      <View style={styles.modeRow}>
        <Pressable
          style={[styles.modeBtn, mode === "activity" && styles.modeBtnActive]}
          onPress={() => { setMode("activity"); setLoading(true); }}
          testID="gps-mode-activity"
        >
          <Text style={[styles.modeText, mode === "activity" && styles.modeTextActive]}>{t("adminGps.modeActivity")}</Text>
        </Pressable>
        <Pressable
          style={[styles.modeBtn, mode === "live" && styles.modeBtnActive]}
          onPress={() => { setMode("live"); setLoading(true); }}
          testID="gps-mode-live"
        >
          <Text style={[styles.modeText, mode === "live" && styles.modeTextActive]}>{t("adminGps.modeLive")}</Text>
        </Pressable>
      </View>

      {mode === "activity" ? (
        <View style={styles.filterRow}>
          {FILTERS.map((f) => (
            <Pressable
              key={f}
              style={[styles.chip, filter === f && styles.chipActive]}
              onPress={() => setFilter(f)}
              testID={`gps-filter-${f}`}
            >
              <Text style={[styles.chipText, filter === f && styles.chipTextActive]}>{filterLabel[f]}</Text>
            </Pressable>
          ))}
        </View>
      ) : (
        <View style={styles.hintRow}>
          <Ionicons name="information-circle-outline" size={14} color={colors.info} />
          <Text style={styles.hintText}>{t("adminGps.liveHint")}</Text>
        </View>
      )}

      {loading ? (
        <Loading text={t("adminGps.loadingPoints")} />
      ) : mode === "live" ? (
        <FlatList
          data={devicePoints}
          keyExtractor={(p) => p.user_id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xxxl }}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Ionicons name="phone-portrait-outline" size={40} color={colors.info} />
              <Text style={styles.emptyText}>{t("adminGps.liveEmpty")}</Text>
            </View>
          }
          renderItem={({ item }) => (
            <Pressable
              style={styles.card}
              onPress={() => openMapCoord(item.lat, item.lng, `${item.name} (${item.store_name})`)}
              testID={`gps-device-${item.user_id}`}
            >
              <View style={[styles.typeIcon, { backgroundColor: colors.brand }]}>
                <Ionicons name="phone-portrait" size={16} color="#fff" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.pn} numberOfLines={1}>{item.name}</Text>
                <Text style={styles.meta}>@{item.username} · {item.store_name}</Text>
                <View style={styles.gpsRow}>
                  <Ionicons name="location" size={12} color={colors.success} />
                  <Text style={styles.gpsText} selectable>{item.lat.toFixed(5)}, {item.lng.toFixed(5)}</Text>
                </View>
                <Text style={styles.date}>{fmtAgo(item.at)}</Text>
              </View>
              <Ionicons name="map" size={22} color={colors.brand} />
            </Pressable>
          )}
        />
      ) : (
        <FlatList
          data={data}
          keyExtractor={(_, i) => String(i)}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xxxl }}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Ionicons name="location-outline" size={40} color={colors.info} />
              <Text style={styles.emptyText}>{t("adminGps.empty")}</Text>
            </View>
          }
          renderItem={({ item }) => (
            <Pressable style={styles.card} onPress={() => openMap(item)} testID={`gps-item-${item.part_number}`}>
              <View style={[styles.typeIcon, { backgroundColor: item.type === "Requirement" ? colors.warning : colors.success }]}>
                <Ionicons name={item.type === "Requirement" ? "help-buoy" : "download"} size={16} color="#000" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.pn} numberOfLines={1}>{item.part_number || "—"}</Text>
                <Text style={styles.meta}>
                  {item.type === "Requirement" ? t("module.requirement") : t("adminGps.purchase")} · {item.store_name} · {item.by || "—"}
                </Text>
                <View style={styles.gpsRow}>
                  <Ionicons name="location" size={12} color={colors.success} />
                  <Text style={styles.gpsText} selectable>{item.gps}</Text>
                </View>
                <Text style={styles.date}>{fmt(item.at)}</Text>
              </View>
              <Ionicons name="map" size={22} color={colors.brand} />
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  modeRow: { flexDirection: "row", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingTop: spacing.md },
  modeBtn: { flex: 1, paddingVertical: spacing.sm, borderRadius: radius.sm, alignItems: "center", backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border },
  modeBtnActive: { backgroundColor: colors.brandFaint, borderColor: colors.brand },
  modeText: { color: colors.info, fontSize: font.sm, fontWeight: "700" },
  modeTextActive: { color: colors.brand },
  hintRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs, paddingHorizontal: spacing.lg, paddingVertical: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.divider },
  hintText: { color: colors.info, fontSize: font.sm - 1, flex: 1 },
  filterRow: { flexDirection: "row", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.divider },
  chip: { paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface2 },
  chipActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  chipText: { color: colors.onSurface2, fontSize: font.sm, fontWeight: "700" },
  chipTextActive: { color: colors.onBrand },
  card: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md, ...shadow.sm },
  typeIcon: { width: 34, height: 34, borderRadius: radius.sm, alignItems: "center", justifyContent: "center" },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  gpsRow: { flexDirection: "row", alignItems: "center", gap: 3, marginTop: 3 },
  gpsText: { color: colors.success, fontSize: font.sm, fontWeight: "700" },
  date: { color: colors.onSurface3, fontSize: font.sm - 1, marginTop: 2 },
  empty: { alignItems: "center", gap: spacing.md, marginTop: spacing.xxxl },
  emptyText: { color: colors.info, fontSize: font.base },
});
