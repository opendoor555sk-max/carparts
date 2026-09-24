import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useAuth } from "@/src/context/AuthContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type QItem = { part_number: string; name?: string; quantity: number; price: number };
type Quotation = {
  id: string;
  quote_number: string;
  customer_name?: string;
  items: QItem[];
  total: number;
  status: "pending" | "accepted" | "rejected" | "converted";
  note?: string;
  by: string;
  at: string;
};

type DraftItem = { part_number: string; name: string; quantity: string; price: string };

const emptyDraftItem = (): DraftItem => ({ part_number: "", name: "", quantity: "1", price: "" });

const statusColor = (s: string) =>
  s === "accepted" || s === "converted" ? colors.success : s === "rejected" ? colors.error : colors.warning;

export default function Quotations() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const { user } = useAuth();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  const [quotes, setQuotes] = useState<Quotation[]>([]);
  const [loading, setLoading] = useState(true);

  const [customerName, setCustomerName] = useState("");
  const [note, setNote] = useState("");
  const [items, setItems] = useState<DraftItem[]>([emptyDraftItem()]);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get("/quotations");
      setQuotes(res);
    } catch (e: any) {
      show(e?.message || t("quotations.loadFailed"), "error");
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

  const updateItem = (idx: number, patch: Partial<DraftItem>) => {
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)));
  };

  const addItemRow = () => setItems((prev) => [...prev, emptyDraftItem()]);

  const removeItemRow = (idx: number) => {
    setItems((prev) => (prev.length === 1 ? prev : prev.filter((_, i) => i !== idx)));
  };

  const createQuote = async () => {
    const cleanItems = items
      .map((it) => ({
        part_number: it.part_number.trim(),
        name: it.name.trim(),
        quantity: parseInt(it.quantity, 10) || 1,
        price: parseFloat(it.price) || 0,
      }))
      .filter((it) => it.part_number);
    if (cleanItems.length === 0) {
      show(t("quotations.errItems"), "error");
      return;
    }
    setSaving(true);
    try {
      await api.post("/quotations", { customer_name: customerName.trim(), items: cleanItems, note: note.trim() });
      show(t("quotations.created"), "success");
      setCustomerName("");
      setNote("");
      setItems([emptyDraftItem()]);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("quotations.createFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  const setStatus = async (id: string, status: "accepted" | "rejected" | "converted") => {
    try {
      await api.patch(`/quotations/${encodeURIComponent(id)}/status`, { status });
      show(t("quotations.updated"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("quotations.updateFailed"), "error");
    }
  };

  const deleteQuote = async (id: string) => {
    try {
      await api.del(`/quotations/${encodeURIComponent(id)}`);
      show(t("quotations.deleted"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("quotations.deleteFailed"), "error");
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("quotations.title")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={t("quotations.title")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="quote-create-card">
            <Text style={styles.cardTitle}>{t("quotations.newQuote").toUpperCase()}</Text>
            <Field
              label={t("quotations.customerName")}
              value={customerName}
              onChangeText={setCustomerName}
              placeholder={t("quotations.customerPlaceholder")}
              testID="quote-customer"
            />

            {items.map((it, idx) => (
              <View key={idx} style={styles.itemRow}>
                <View style={{ flex: 2 }}>
                  <Field
                    label={t("quotations.partNumber")}
                    value={it.part_number}
                    onChangeText={(v) => updateItem(idx, { part_number: v })}
                    testID={`quote-item-part-${idx}`}
                  />
                </View>
                <View style={{ flex: 1 }}>
                  <Field
                    label={t("quotations.qty")}
                    value={it.quantity}
                    onChangeText={(v) => updateItem(idx, { quantity: v })}
                    keyboardType="numeric"
                    testID={`quote-item-qty-${idx}`}
                  />
                </View>
                <View style={{ flex: 1 }}>
                  <Field
                    label={t("quotations.price")}
                    value={it.price}
                    onChangeText={(v) => updateItem(idx, { price: v })}
                    keyboardType="numeric"
                    testID={`quote-item-price-${idx}`}
                  />
                </View>
                {items.length > 1 ? (
                  <Button
                    title={t("common.delete")}
                    onPress={() => removeItemRow(idx)}
                    variant="ghost"
                    icon="trash"
                    testID={`quote-item-remove-${idx}`}
                  />
                ) : null}
              </View>
            ))}
            <Button
              title={t("quotations.addItem")}
              onPress={addItemRow}
              variant="secondary"
              icon="add-circle"
              testID="quote-add-item"
            />

            <Field label={t("common.noteOptional")} value={note} onChangeText={setNote} testID="quote-note" />
            <Button
              title={t("quotations.save")}
              onPress={createQuote}
              loading={saving}
              icon="checkmark-circle"
              testID="quote-save"
            />
          </Card>

          <Card testID="quote-list-card">
            <Text style={styles.cardTitle}>{t("quotations.history").toUpperCase()}</Text>
            {quotes.length === 0 ? (
              <EmptyState icon="document-text-outline" title={t("quotations.noQuotes")} />
            ) : (
              quotes.map((q) => (
                <View key={q.id} style={styles.qCard} testID={`quote-${q.id}`}>
                  <View style={styles.qHeader}>
                    <Text style={styles.qNumber}>{q.quote_number}</Text>
                    <View style={[styles.statusPill, { backgroundColor: statusColor(q.status) + "22" }]}>
                      <Text style={[styles.statusText, { color: statusColor(q.status) }]}>
                        {t(`quotations.status.${q.status}` as any)}
                      </Text>
                    </View>
                  </View>
                  {q.customer_name ? <Text style={styles.qCustomer}>{q.customer_name}</Text> : null}
                  {q.items.map((it, i) => (
                    <Text key={i} style={styles.qItem}>
                      • {it.part_number} {it.name ? `(${it.name})` : ""} × {it.quantity} @ ₹{it.price}
                    </Text>
                  ))}
                  <Text style={styles.qTotal}>{t("quotations.total")}: ₹{q.total.toFixed(2)}</Text>
                  <Text style={styles.qMeta}>
                    {new Date(q.at).toLocaleString()} • {q.by}
                  </Text>
                  {q.status === "pending" ? (
                    <View style={styles.qActions}>
                      <Button
                        title={t("quotations.accept")}
                        onPress={() => setStatus(q.id, "accepted")}
                        variant="primary"
                        icon="checkmark-done"
                        style={{ flex: 1 }}
                        testID={`quote-accept-${q.id}`}
                      />
                      <Button
                        title={t("quotations.reject")}
                        onPress={() => setStatus(q.id, "rejected")}
                        variant="danger"
                        icon="close-circle"
                        style={{ flex: 1 }}
                        testID={`quote-reject-${q.id}`}
                      />
                    </View>
                  ) : null}
                  {q.status === "accepted" ? (
                    <Button
                      title={t("quotations.markConverted")}
                      onPress={() => setStatus(q.id, "converted")}
                      variant="primary"
                      icon="swap-horizontal"
                      testID={`quote-convert-${q.id}`}
                    />
                  ) : null}
                  {isAdmin ? (
                    <Button
                      title={t("common.delete")}
                      onPress={() => deleteQuote(q.id)}
                      variant="ghost"
                      icon="trash"
                      testID={`quote-delete-${q.id}`}
                    />
                  ) : null}
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
  itemRow: { flexDirection: "row", gap: spacing.sm, alignItems: "flex-end" },
  qCard: { paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  qHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  qNumber: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  statusPill: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.sm },
  statusText: { fontSize: font.sm - 1, fontWeight: "800" },
  qCustomer: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  qItem: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  qTotal: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", marginTop: spacing.xs },
  qMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  qActions: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.sm },
});
