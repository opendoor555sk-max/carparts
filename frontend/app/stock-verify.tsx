import { useCallback, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, EmptyState, Header, Loading, StatusChip } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Item = { part_number: string; expected: number; part_name?: string; company?: string };

export default function StockVerify() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const [items, setItems] = useState<Item[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [report, setReport] = useState<any>(null);

  const load = useCallback(async () => {
    try {
      const res = await api.get<{ items: Item[] }>("/stock/verification");
      setItems(res.items);
      setCounts(Object.fromEntries(res.items.map((i) => [i.part_number, 0])));
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [show]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      setReport(null);
      load();
    }, [load]),
  );

  const setCount = (pn: string, v: number) => setCounts((c) => ({ ...c, [pn]: Math.max(0, v) }));

  const submit = async () => {
    setSubmitting(true);
    try {
      const payload = { counts: items.map((i) => ({ part_number: i.part_number, counted: counts[i.part_number] || 0 })) };
      const res = await api.post("/stock/verify", payload);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      setReport(res);
    } catch (e: any) {
      show(e?.message || "નિષ્ફળ", "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Stock Verification" subtitle="ફિઝિકલ સ્ટોક ગણો" onBack={() => router.back()} />
      {loading ? (
        <Loading />
      ) : items.length === 0 ? (
        <EmptyState icon="cube-outline" title="કોઈ stock નથી" subtitle="Buy module થી stock add કરો" />
      ) : (
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: insets.bottom + 120 }}>
          <View style={styles.info}>
            <Ionicons name="clipboard" size={18} color={colors.brand} />
            <Text style={styles.infoText}>
              દરેક part ના ફિઝિકલ (હાથમાં) ગણેલા units નાખો. System ના stock સાથે compare થઈ કયો part ગાયબ છે એ બતાવશે.
            </Text>
          </View>

          {report ? (
            <Card testID="verify-report">
              <Text style={styles.cardTitle}>REPORT</Text>
              <View style={styles.summaryRow}>
                <View style={styles.summaryBox}>
                  <Text style={styles.summaryNum}>{report.total_parts}</Text>
                  <Text style={styles.summaryLbl}>Total parts</Text>
                </View>
                <View style={styles.summaryBox}>
                  <Text style={[styles.summaryNum, { color: colors.success }]}>{report.ok_count}</Text>
                  <Text style={styles.summaryLbl}>બરાબર</Text>
                </View>
                <View style={styles.summaryBox}>
                  <Text style={[styles.summaryNum, { color: colors.error }]}>{report.discrepancies.length}</Text>
                  <Text style={styles.summaryLbl}>તફાવત</Text>
                </View>
              </View>
              {report.discrepancies.length === 0 ? (
                <Text style={styles.allGood}>✅ બધો stock બરાબર મળ્યો!</Text>
              ) : (
                report.discrepancies.map((d: any) => (
                  <View key={d.part_number} style={styles.discRow}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.discPn}>{d.part_number}</Text>
                      {d.part_name ? <Text style={styles.discName}>{d.part_name}</Text> : null}
                      <Text style={styles.discDetail}>
                        System: {d.expected}  •  ગણ્યા: {d.counted}  •  {d.diff > 0 ? `+${d.diff}` : d.diff}
                      </Text>
                    </View>
                    <StatusChip status={d.status === "MISSING" ? "Cancelled" : "Pending"} />
                  </View>
                ))
              )}
              <Button title="ફરી ગણો" variant="secondary" onPress={() => setReport(null)} style={{ marginTop: spacing.md }} testID="verify-again" />
            </Card>
          ) : (
            <>
              {items.map((i) => (
                <Card key={i.part_number} testID={`vrow-${i.part_number}`}>
                  <View style={styles.rowBetween}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.pn}>{i.part_number}</Text>
                      {i.part_name ? <Text style={styles.name}>{i.part_name}</Text> : null}
                      <Text style={styles.expected}>System stock: {i.expected}</Text>
                    </View>
                  </View>
                  <View style={styles.counterRow}>
                    <Pressable style={styles.cBtn} onPress={() => setCount(i.part_number, (counts[i.part_number] || 0) - 1)} testID={`vdec-${i.part_number}`}>
                      <Ionicons name="remove" size={20} color={colors.onSurface} />
                    </Pressable>
                    <Text style={styles.cVal}>{counts[i.part_number] || 0}</Text>
                    <Pressable style={styles.cBtn} onPress={() => setCount(i.part_number, (counts[i.part_number] || 0) + 1)} testID={`vinc-${i.part_number}`}>
                      <Ionicons name="add" size={20} color={colors.onSurface} />
                    </Pressable>
                    <Pressable style={styles.okBtn} onPress={() => setCount(i.part_number, i.expected)} testID={`vok-${i.part_number}`}>
                      <Text style={styles.okBtnText}>= {i.expected}</Text>
                    </Pressable>
                  </View>
                </Card>
              ))}
            </>
          )}
        </ScrollView>
      )}

      {!loading && !report && items.length > 0 ? (
        <View style={[styles.bottomBar, { paddingBottom: insets.bottom + spacing.md }]}>
          <Button title="Verify કરો & Report જુઓ" onPress={submit} loading={submitting} icon="checkmark-done" testID="verify-submit" />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  info: { flexDirection: "row", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  infoText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 18 },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  pn: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800", letterSpacing: 0.5 },
  name: { color: colors.onSurface3, fontSize: font.base, marginTop: 2 },
  expected: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  counterRow: { flexDirection: "row", alignItems: "center", gap: spacing.md, marginTop: spacing.md },
  cBtn: { width: 44, height: 44, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center", backgroundColor: colors.surface },
  cVal: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", minWidth: 40, textAlign: "center" },
  okBtn: { marginLeft: "auto", borderWidth: 1, borderColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: 10 },
  okBtnText: { color: colors.brand, fontSize: font.base, fontWeight: "800" },
  bottomBar: { position: "absolute", left: 0, right: 0, bottom: 0, padding: spacing.lg, paddingTop: spacing.md, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  summaryRow: { flexDirection: "row", gap: spacing.md, marginBottom: spacing.md },
  summaryBox: { flex: 1, alignItems: "center", backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingVertical: spacing.md },
  summaryNum: { color: colors.onSurface, fontSize: font.xxl, fontWeight: "800" },
  summaryLbl: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  allGood: { color: colors.success, fontSize: font.base, fontWeight: "700", textAlign: "center", paddingVertical: spacing.md },
  discRow: { flexDirection: "row", alignItems: "center", gap: spacing.md, paddingVertical: spacing.sm, borderTopWidth: 1, borderTopColor: colors.divider },
  discPn: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  discName: { color: colors.onSurface3, fontSize: font.sm, marginTop: 1 },
  discDetail: { color: colors.info, fontSize: font.sm, marginTop: 2 },
});
