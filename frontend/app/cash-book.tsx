import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useAuth } from "@/src/context/AuthContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, spacing } from "@/src/theme";

type CashEntry = {
  id: string;
  type: "in" | "out";
  amount: number;
  note?: string;
  running_balance: number;
  by: string;
  at: string;
};

export default function CashBook() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const { user } = useAuth();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  const [entries, setEntries] = useState<CashEntry[]>([]);
  const [balance, setBalance] = useState(0);
  const [loading, setLoading] = useState(true);

  const [entryType, setEntryType] = useState<"in" | "out">("in");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get("/cash-book");
      setEntries(res.entries);
      setBalance(res.balance);
    } catch (e: any) {
      show(e?.message || t("cashBook.loadFailed"), "error");
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

  const addEntry = async () => {
    const amt = parseFloat(amount);
    if (!amt || amt <= 0) {
      show(t("cashBook.errAmount"), "error");
      return;
    }
    setSaving(true);
    try {
      await api.post("/cash-book", { type: entryType, amount: amt, note: note.trim() });
      show(t("cashBook.added"), "success");
      setAmount("");
      setNote("");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("cashBook.addFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  const deleteEntry = async (id: string) => {
    try {
      await api.del(`/cash-book/${encodeURIComponent(id)}`);
      show(t("cashBook.deleted"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("cashBook.deleteFailed"), "error");
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("cashBook.title")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={t("cashBook.title")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="cashbook-balance-card">
            <Text style={styles.cardTitle}>{t("cashBook.cashInHand").toUpperCase()}</Text>
            <Text style={[styles.balanceBig, { color: balance < 0 ? colors.error : colors.success }]}>
              ₹{balance.toFixed(2)}
            </Text>
          </Card>

          <Card>
            <Text style={styles.cardTitle}>{t("cashBook.addEntry").toUpperCase()}</Text>
            <View style={styles.typeRow}>
              <Button
                title={t("cashBook.cashIn")}
                onPress={() => setEntryType("in")}
                variant={entryType === "in" ? "primary" : "secondary"}
                icon="arrow-down-circle"
                style={{ flex: 1 }}
                testID="cash-type-in"
              />
              <Button
                title={t("cashBook.cashOut")}
                onPress={() => setEntryType("out")}
                variant={entryType === "out" ? "danger" : "secondary"}
                icon="arrow-up-circle"
                style={{ flex: 1 }}
                testID="cash-type-out"
              />
            </View>
            <Field
              label={t("cashBook.amount")}
              value={amount}
              onChangeText={setAmount}
              placeholder="₹ 0"
              keyboardType="numeric"
              testID="cash-amount"
            />
            <Field
              label={t("common.noteOptional")}
              value={note}
              onChangeText={setNote}
              placeholder={t("cashBook.notePlaceholder")}
              testID="cash-note"
            />
            <Button title={t("cashBook.save")} onPress={addEntry} loading={saving} icon="checkmark-circle" testID="cash-save" />
          </Card>

          <Card testID="cashbook-list-card">
            <Text style={styles.cardTitle}>{t("cashBook.history").toUpperCase()}</Text>
            {entries.length === 0 ? (
              <EmptyState icon="wallet-outline" title={t("cashBook.noEntries")} />
            ) : (
              entries.map((e) => (
                <View key={e.id} style={styles.entryRow} testID={`cash-entry-${e.id}`}>
                  <Ionicons
                    name={e.type === "in" ? "arrow-down-circle" : "arrow-up-circle"}
                    size={22}
                    color={e.type === "in" ? colors.success : colors.error}
                  />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.entryTitle}>
                      {e.type === "in" ? t("cashBook.cashIn") : t("cashBook.cashOut")}
                    </Text>
                    {e.note ? <Text style={styles.entryNote}>{e.note}</Text> : null}
                    <Text style={styles.entryMeta}>
                      {new Date(e.at).toLocaleString()} • {e.by}
                    </Text>
                  </View>
                  <View style={{ alignItems: "flex-end" }}>
                    <Text style={[styles.entryAmount, { color: e.type === "in" ? colors.success : colors.error }]}>
                      {e.type === "in" ? "+" : "−"}₹{e.amount.toFixed(2)}
                    </Text>
                    <Text style={styles.entryRunning}>
                      {t("cashBook.bal")}: ₹{e.running_balance.toFixed(2)}
                    </Text>
                    {isAdmin ? (
                      <Button
                        title={t("common.delete")}
                        onPress={() => deleteEntry(e.id)}
                        variant="ghost"
                        icon="trash"
                        testID={`cash-delete-${e.id}`}
                      />
                    ) : null}
                  </View>
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
  balanceBig: { fontSize: 40, fontWeight: "900", marginTop: spacing.sm },
  typeRow: { flexDirection: "row", gap: spacing.sm, marginBottom: spacing.sm },
  entryRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  entryTitle: { color: colors.onSurface, fontSize: font.base, fontWeight: "700" },
  entryNote: { color: colors.onSurface2, fontSize: font.sm, marginTop: 1 },
  entryMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  entryAmount: { fontSize: font.base, fontWeight: "900" },
  entryRunning: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
});
