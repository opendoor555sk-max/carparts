import { useCallback, useState } from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, EmptyState, Header, Loading, Meter, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function AiApprovals() {
  const router = useRouter();
  const { show } = useToast();
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      setItems(await api.get("/ai/research?status=Pending"));
    } catch {
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const approve = async (id: string) => {
    try {
      await api.post(`/ai/research/${id}/approve`);
      show("Approved & Verified", "success");
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };
  const reject = async (id: string) => {
    try {
      await api.post(`/ai/research/${id}/reject`);
      show("Rejected", "info");
      load();
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="AI Approvals" subtitle="Gemini research pending" onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : items.length === 0 ? (
        <EmptyState icon="sparkles" title="No pending AI research" subtitle="Run AI research from a part detail" />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(i) => i.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}
          renderItem={({ item }) => (
            <Card testID={`ai-pending-${item.part_number}`}>
              <View style={styles.rowBetween}>
                <Text style={styles.pn}>{item.part_number}</Text>
                <StatusChip status={item.verification} />
              </View>
              <Meter
                value={item.confidence || 0}
                color={item.confidence >= 70 ? colors.success : item.confidence >= 40 ? colors.warning : colors.error}
                label="Confidence"
              />
              <Text style={styles.detail}>{item.result?.name || "—"} • {item.result?.category || "—"}</Text>
              <Text style={styles.dim}>{(item.result?.compatible_vehicles || []).join(", ")}</Text>
              {item.conflict ? (
                <View style={styles.conflict}>
                  <Ionicons name="warning" size={14} color={colors.onWarning} />
                  <Text style={styles.conflictText}>Information Conflict — Admin Verification Required</Text>
                </View>
              ) : null}
              <View style={styles.actions}>
                <Button title="Approve" onPress={() => approve(item.id)} icon="checkmark" style={{ flex: 1 }} testID={`approve-${item.part_number}`} />
                <Button title="Reject" onPress={() => reject(item.id)} variant="danger" icon="close" style={{ flex: 1 }} testID={`reject-${item.part_number}`} />
              </View>
            </Card>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.md },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  detail: { color: colors.onSurface2, fontSize: font.base, marginTop: spacing.md, fontWeight: "600" },
  dim: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  conflict: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.warning, borderRadius: radius.sm, padding: spacing.sm, marginTop: spacing.md },
  conflictText: { color: colors.onWarning, fontSize: font.sm, fontWeight: "700", flex: 1 },
  actions: { flexDirection: "row", gap: spacing.md, marginTop: spacing.lg },
});
