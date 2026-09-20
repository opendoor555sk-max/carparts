import { useCallback, useState } from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, EmptyState, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

type SearchLog = {
  id: string;
  user_id: string;
  user_name: string;
  role?: string;
  store_id: string | null;
  part_number_searched: string;
  gps: string | null;
  gps_coord: { lat: number; lng: number } | null;
  created_at: string;
};

const PAGE_SIZE = 50;

export default function SearchLogs() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();

  const [logs, setLogs] = useState<SearchLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: SearchLog[]; total: number }>(
        `/admin/search-logs?page=1&page_size=${PAGE_SIZE}`,
      );
      setLogs(res.items);
      setTotal(res.total);
      setPage(1);
    } catch (e: any) {
      show(e?.message || t("common.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [show, t]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const next = page + 1;
      const res = await api.get<{ items: SearchLog[]; total: number }>(
        `/admin/search-logs?page=${next}&page_size=${PAGE_SIZE}`,
      );
      setLogs((cur) => [...cur, ...res.items]);
      setTotal(res.total);
      setPage(next);
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title={t("searchLogs.title")} subtitle={t("searchLogs.subtitle")} onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : logs.length === 0 ? (
        <EmptyState icon="time-outline" title={t("searchLogs.empty")} />
      ) : (
        <FlatList
          data={logs}
          keyExtractor={(l) => l.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}
          renderItem={({ item }) => (
            <View style={styles.row} testID={`search-log-${item.id}`}>
              <View style={styles.rowTop}>
                <Text style={styles.name} numberOfLines={1}>{item.user_name}</Text>
                <Text style={styles.time}>{new Date(item.created_at).toLocaleString()}</Text>
              </View>
              <View style={styles.pnRow}>
                <Ionicons name="search" size={14} color={colors.brand} />
                <Text style={styles.pn}>{item.part_number_searched}</Text>
              </View>
              <View style={styles.locRow}>
                <Ionicons name="location-outline" size={13} color={colors.info} />
                <Text style={styles.loc}>
                  {item.gps_coord ? `${item.gps_coord.lat.toFixed(5)}, ${item.gps_coord.lng.toFixed(5)}` : t("searchLogs.noLocation")}
                </Text>
              </View>
            </View>
          )}
          ListFooterComponent={
            logs.length < total ? (
              <View style={{ marginTop: spacing.md }}>
                <Button
                  title={loadingMore ? t("common.loading") : t("searchLogs.loadMore")}
                  onPress={loadMore}
                  variant="secondary"
                  icon="chevron-down"
                  loading={loadingMore}
                  testID="search-logs-load-more"
                />
              </View>
            ) : null
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  row: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.lg,
    gap: spacing.xs,
    ...shadow.sm,
  },
  rowTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.sm },
  name: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", flex: 1 },
  time: { color: colors.info, fontSize: font.sm - 1 },
  pnRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  pn: { color: colors.brand, fontSize: font.lg, fontWeight: "800" },
  locRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  loc: { color: colors.info, fontSize: font.sm },
});
