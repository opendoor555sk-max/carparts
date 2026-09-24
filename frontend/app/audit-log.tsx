import { useCallback, useState } from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { EmptyState, Header, Loading } from "@/src/components/ui";
import { colors, font, spacing } from "@/src/theme";

type LogEntry = {
  id: string;
  action: string;
  details?: string;
  by: string;
  at: string;
};

const actionIcon = (action: string): keyof typeof Ionicons.glyphMap => {
  if (action.startsWith("cash_book")) return "wallet";
  if (action.startsWith("quotation")) return "document-text";
  if (action.startsWith("purchase_order")) return "clipboard";
  if (action.startsWith("reservation")) return "lock-closed";
  if (action.startsWith("stock_transfer")) return "swap-horizontal";
  return "time";
};

export default function AuditLog() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();

  const [rows, setRows] = useState<LogEntry[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const res = await api.get<LogEntry[]>("/audit-log");
      setRows(res);
    } catch (e: any) {
      show(e?.message || t("auditLog.loadFailed"), "error");
    } finally {
      setLoading(false);
    }
  }, [show, t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("auditLog.title")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={t("auditLog.title")} onBack={() => router.back()} />
      {rows.length === 0 ? (
        <EmptyState icon="time-outline" title={t("auditLog.noEntries")} />
      ) : (
        <FlatList
          data={rows}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm }}
          renderItem={({ item }) => (
            <View style={styles.row} testID={`audit-${item.id}`}>
              <Ionicons name={actionIcon(item.action)} size={20} color={colors.brand} />
              <View style={{ flex: 1 }}>
                <Text style={styles.action}>{item.action}</Text>
                {item.details ? <Text style={styles.details}>{item.details}</Text> : null}
                <Text style={styles.meta}>
                  {new Date(item.at).toLocaleString()} • {item.by}
                </Text>
              </View>
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  row: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: spacing.sm,
    backgroundColor: colors.surface2,
    borderRadius: 12,
    padding: spacing.md,
  },
  action: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  details: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  meta: { color: colors.info, fontSize: font.sm - 1, marginTop: 4 },
});
