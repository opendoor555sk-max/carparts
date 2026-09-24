import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, spacing } from "@/src/theme";

type Store = { id: string; name: string };
type Transfer = {
  id: string;
  part_number: string;
  quantity: number;
  from_store_id: string;
  to_store_id: string;
  note?: string;
  by: string;
  at: string;
};

export default function StockTransfer() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();

  const [stores, setStores] = useState<Store[]>([]);
  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const [loading, setLoading] = useState(true);

  const [fromStore, setFromStore] = useState<string | null>(null);
  const [toStore, setToStore] = useState<string | null>(null);
  const [partNumber, setPartNumber] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, tr] = await Promise.all([
        api.get<Store[]>("/admin/stores"),
        api.get<Transfer[]>("/stock-transfer"),
      ]);
      setStores(s);
      setTransfers(tr);
    } catch (e: any) {
      show(e?.message || t("stockTransfer.loadFailed"), "error");
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

  const storeName = (id: string) => stores.find((s) => s.id === id)?.name || id;

  const doTransfer = async () => {
    if (!fromStore || !toStore) {
      show(t("stockTransfer.errStores"), "error");
      return;
    }
    if (fromStore === toStore) {
      show(t("stockTransfer.errSame"), "error");
      return;
    }
    const pn = partNumber.trim();
    const qty = parseInt(quantity, 10) || 0;
    if (!pn || qty <= 0) {
      show(t("stockTransfer.errPart"), "error");
      return;
    }
    setSaving(true);
    try {
      await api.post("/stock-transfer", {
        from_store_id: fromStore,
        to_store_id: toStore,
        part_number: pn,
        quantity: qty,
        note: note.trim(),
      });
      show(t("stockTransfer.done"), "success");
      setPartNumber("");
      setQuantity("1");
      setNote("");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("stockTransfer.failed"), "error");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("stockTransfer.title")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={t("stockTransfer.title")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="transfer-create-card">
            <Text style={styles.cardTitle}>{t("stockTransfer.newTransfer").toUpperCase()}</Text>

            <Text style={styles.label}>{t("stockTransfer.fromStore")}</Text>
            <View style={styles.storeRow}>
              {stores.map((s) => (
                <Button
                  key={s.id}
                  title={s.name}
                  onPress={() => setFromStore(s.id)}
                  variant={fromStore === s.id ? "primary" : "secondary"}
                  testID={`transfer-from-${s.id}`}
                />
              ))}
            </View>

            <Text style={styles.label}>{t("stockTransfer.toStore")}</Text>
            <View style={styles.storeRow}>
              {stores.map((s) => (
                <Button
                  key={s.id}
                  title={s.name}
                  onPress={() => setToStore(s.id)}
                  variant={toStore === s.id ? "primary" : "secondary"}
                  testID={`transfer-to-${s.id}`}
                />
              ))}
            </View>

            <Field
              label={t("stockTransfer.partNumber")}
              value={partNumber}
              onChangeText={setPartNumber}
              testID="transfer-part"
            />
            <Field
              label={t("stockTransfer.qty")}
              value={quantity}
              onChangeText={setQuantity}
              keyboardType="numeric"
              testID="transfer-qty"
            />
            <Field label={t("common.noteOptional")} value={note} onChangeText={setNote} testID="transfer-note" />

            <Button
              title={t("stockTransfer.save")}
              onPress={doTransfer}
              loading={saving}
              icon="swap-horizontal"
              testID="transfer-save"
            />
          </Card>

          <Card testID="transfer-list-card">
            <Text style={styles.cardTitle}>{t("stockTransfer.history").toUpperCase()}</Text>
            {transfers.length === 0 ? (
              <EmptyState icon="swap-horizontal-outline" title={t("stockTransfer.noTransfers")} />
            ) : (
              transfers.map((tr) => (
                <View key={tr.id} style={styles.trRow} testID={`transfer-${tr.id}`}>
                  <Text style={styles.trTitle}>
                    {tr.part_number} × {tr.quantity}
                  </Text>
                  <Text style={styles.trRoute}>
                    {storeName(tr.from_store_id)} → {storeName(tr.to_store_id)}
                  </Text>
                  {tr.note ? <Text style={styles.trNote}>{tr.note}</Text> : null}
                  <Text style={styles.trMeta}>
                    {new Date(tr.at).toLocaleString()} • {tr.by}
                  </Text>
                </View>
              ))
            )}
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  label: { color: colors.onSurface2, fontSize: font.sm, fontWeight: "700", marginBottom: spacing.xs, marginTop: spacing.xs },
  storeRow: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm, marginBottom: spacing.md },
  trRow: { paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  trTitle: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  trRoute: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  trNote: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  trMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
});
