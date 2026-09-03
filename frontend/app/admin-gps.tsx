import { useCallback, useMemo, useState } from "react";
import { FlatList, Linking, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

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

const FILTERS = ["All", "Requirement", "Purchase"] as const;

export default function AdminGps() {
  const router = useRouter();
  const { show } = useToast();
  const [points, setPoints] = useState<GpsPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("All");

  const load = useCallback(async () => {
    try {
      setPoints(await api.get<GpsPoint[]>("/admin/gps-locations"));
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [show]);

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

  const openMap = (p: GpsPoint) => {
    const label = encodeURIComponent(`${p.part_number} (${p.store_name})`);
    const url = Platform.select({
      ios: `https://maps.google.com/?q=${p.lat},${p.lng}(${label})`,
      android: `https://maps.google.com/?q=${p.lat},${p.lng}(${label})`,
      default: `https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lng}`,
    })!;
    Linking.openURL(url).catch(() => show("Could not open Maps", "error"));
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
      <Header title="GPS Locations" subtitle="All stores — tap to open in Maps" onBack={() => router.back()} />
      <View style={styles.filterRow}>
        {FILTERS.map((f) => (
          <Pressable
            key={f}
            style={[styles.chip, filter === f && styles.chipActive]}
            onPress={() => setFilter(f)}
            testID={`gps-filter-${f}`}
          >
            <Text style={[styles.chipText, filter === f && styles.chipTextActive]}>{f}</Text>
          </Pressable>
        ))}
      </View>
      {loading ? (
        <Loading text="Loading GPS points…" />
      ) : (
        <FlatList
          data={data}
          keyExtractor={(_, i) => String(i)}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: spacing.xxxl }}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Ionicons name="location-outline" size={40} color={colors.info} />
              <Text style={styles.emptyText}>No GPS points captured yet</Text>
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
                  {item.type} · {item.store_name} · {item.by || "—"}
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
  filterRow: { flexDirection: "row", gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.divider },
  chip: { paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface2 },
  chipActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  chipText: { color: colors.onSurface2, fontSize: font.sm, fontWeight: "700" },
  chipTextActive: { color: colors.onBrand },
  card: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  typeIcon: { width: 34, height: 34, borderRadius: radius.sm, alignItems: "center", justifyContent: "center" },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  meta: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  gpsRow: { flexDirection: "row", alignItems: "center", gap: 3, marginTop: 3 },
  gpsText: { color: colors.success, fontSize: font.sm, fontWeight: "700" },
  date: { color: colors.onSurface3, fontSize: font.sm - 1, marginTop: 2 },
  empty: { alignItems: "center", gap: spacing.md, marginTop: spacing.xxxl },
  emptyText: { color: colors.info, fontSize: font.base },
});
