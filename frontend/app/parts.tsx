import { useCallback, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { Button, EmptyState, Header, Loading, StatusChip } from "@/src/components/ui";
import { useAuth } from "@/src/context/AuthContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

export default function PartsList() {
  const { category, company, title } = useLocalSearchParams<{ category: string; company: string; title: string }>();
  const { t } = useLanguage();
  const { can } = useAuth();
  const canViewDetails = can("view_part_details");
  const router = useRouter();
  const [parts, setParts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Previously a bare try/catch {} — a fetch failure (e.g. a cold-start
  // network hiccup) silently left parts=[] and looked identical to "this
  // category really has zero parts". Surface the failure instead, with a
  // way to retry the same request rather than requiring a full app restart.
  const load = useCallback(async () => {
    setError(null);
    try {
      const params = new URLSearchParams();
      if (category) params.append("category", category as string);
      if (company && company !== "All") params.append("company", company as string);
      setParts(await api.get(`/parts?${params.toString()}`));
    } catch (e: any) {
      setError(e?.message || t("common.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [category, company, t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  return (
    <View style={styles.flex}>
      <Header title={(title as string) || t("parts.title")} subtitle={t("parts.subtitle")} onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : error ? (
        <EmptyState
          icon="cloud-offline-outline"
          title={t("common.loadFailed")}
          subtitle={error}
          action={<Button title={t("common.retry")} onPress={load} icon="refresh" testID="parts-retry" />}
        />
      ) : parts.length === 0 ? (
        <EmptyState icon="documents-outline" title={t("parts.empty")} subtitle={t("parts.emptySub")} />
      ) : (
        <FlatList
          data={parts}
          keyExtractor={(p) => p.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}
          renderItem={({ item }) =>
            canViewDetails ? (
              <Pressable
                style={styles.row}
                onPress={() => router.push(`/part/${encodeURIComponent(item.part_number)}` as any)}
                testID={`partlist-${item.part_number}`}
              >
                <View style={{ flex: 1 }}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                  {item.name ? <Text style={styles.name}>{item.name}</Text> : null}
                  <Text style={styles.meta}>{item.company} • {t("common.stock")}: {item.stock_count ?? 0}</Text>
                </View>
                <StatusChip status={item.verification_status} />
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            ) : (
              // Reduced view: only part number + existence -- the backend
              // itself doesn't send name/company/stock_count/verification
              // status here without view_part_details.
              <Pressable
                style={styles.row}
                onPress={() => router.push(`/part/${encodeURIComponent(item.part_number)}` as any)}
                testID={`partlist-${item.part_number}`}
              >
                <View style={{ flex: 1 }}>
                  <Text style={styles.pn}>{item.part_number}</Text>
                </View>
                <View
                  style={[
                    styles.existenceBadge,
                    { backgroundColor: item.exists ? colors.successFaint : colors.errorFaint },
                  ]}
                >
                  <Text style={[styles.existenceBadgeText, { color: item.exists ? colors.success : colors.error }]}>
                    {item.exists ? t("partDetail.inStockYes") : t("partDetail.notFoundInStore")}
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.info} />
              </Pressable>
            )
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg, ...shadow.sm },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  meta: { color: colors.info, fontSize: font.sm, marginTop: spacing.xs },
  existenceBadge: { paddingHorizontal: spacing.sm, paddingVertical: 4, borderRadius: radius.pill },
  existenceBadgeText: { fontSize: font.sm - 1, fontWeight: "800" },
});
