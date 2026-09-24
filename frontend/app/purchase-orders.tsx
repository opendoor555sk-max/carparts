import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { useAuth } from "@/src/context/AuthContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type POItem = { part_number: string; name?: string; quantity: number; price: number };
type PurchaseOrder = {
  id: string;
  po_number: string;
  vendor_name?: string;
  items: POItem[];
  total: number;
  status: "pending" | "received" | "cancelled";
  note?: string;
  by: string;
  at: string;
};

type DraftItem = { part_number: string; name: string; quantity: string; price: string };

const emptyDraftItem = (): DraftItem => ({ part_number: "", name: "", quantity: "1", price: "" });

const statusColor = (s: string) =>
  s === "received" ? colors.success : s === "cancelled" ? colors.error : colors.warning;

export default function PurchaseOrders() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const { user } = useAuth();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [loading, setLoading] = useState(true);

  const [vendorName, setVendorName] = useState("");
  const [note, setNote] = useState("");
  const [items, setItems] = useState<DraftItem[]>([emptyDraftItem()]);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get("/purchase-orders");
      setOrders(res);
    } catch (e: any) {
      show(e?.message || t("purchaseOrders.loadFailed"), "error");
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

  const createOrder = async () => {
    const cleanItems = items
      .map((it) => ({
        part_number: it.part_number.trim(),
        name: it.name.trim(),
        quantity: parseInt(it.quantity, 10) || 1,
        price: parseFloat(it.price) || 0,
      }))
      .filter((it) => it.part_number);
    if (cleanItems.length === 0) {
      show(t("purchaseOrders.errItems"), "error");
      return;
    }
    setSaving(true);
    try {
      await api.post("/purchase-orders", { vendor_name: vendorName.trim(), items: cleanItems, note: note.trim() });
      show(t("purchaseOrders.created"), "success");
      setVendorName("");
      setNote("");
      setItems([emptyDraftItem()]);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("purchaseOrders.createFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  const setStatus = async (id: string, status: "pending" | "received" | "cancelled") => {
    try {
      await api.patch(`/purchase-orders/${encodeURIComponent(id)}/status`, { status });
      show(t("purchaseOrders.updated"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("purchaseOrders.updateFailed"), "error");
    }
  };

  const deleteOrder = async (id: string) => {
    try {
      await api.del(`/purchase-orders/${encodeURIComponent(id)}`);
      show(t("purchaseOrders.deleted"), "success");
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("purchaseOrders.deleteFailed"), "error");
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("purchaseOrders.title")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={t("purchaseOrders.title")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="po-create-card">
            <Text style={styles.cardTitle}>{t("purchaseOrders.newOrder").toUpperCase()}</Text>
            <Field
              label={t("purchaseOrders.vendorName")}
              value={vendorName}
              onChangeText={setVendorName}
              placeholder={t("purchaseOrders.vendorPlaceholder")}
              testID="po-vendor"
            />

            {items.map((it, idx) => (
              <View key={idx} style={styles.itemRow}>
                <View style={{ flex: 2 }}>
                  <Field
                    label={t("purchaseOrders.partNumber")}
                    value={it.part_number}
                    onChangeText={(v) => updateItem(idx, { part_number: v })}
                    testID={`po-item-part-${idx}`}
                  />
                </View>
                <View style={{ flex: 1 }}>
                  <Field
                    label={t("purchaseOrders.qty")}
                    value={it.quantity}
                    onChangeText={(v) => updateItem(idx, { quantity: v })}
                    keyboardType="numeric"
                    testID={`po-item-qty-${idx}`}
                  />
                </View>
                <View style={{ flex: 1 }}>
                  <Field
                    label={t("purchaseOrders.price")}
                    value={it.price}
                    onChangeText={(v) => updateItem(idx, { price: v })}
                    keyboardType="numeric"
                    testID={`po-item-price-${idx}`}
                  />
                </View>
                {items.length > 1 ? (
                  <Button
                    title={t("common.delete")}
                    onPress={() => removeItemRow(idx)}
                    variant="ghost"
                    icon="trash"
                    testID={`po-item-remove-${idx}`}
                  />
                ) : null}
              </View>
            ))}
            <Button
              title={t("purchaseOrders.addItem")}
              onPress={addItemRow}
              variant="secondary"
              icon="add-circle"
              testID="po-add-item"
            />

            <Field
              label={t("common.noteOptional")}
              value={note}
              onChangeText={setNote}
              testID="po-note"
            />
            <Button
              title={t("purchaseOrders.save")}
              onPress={createOrder}
              loading={saving}
              icon="checkmark-circle"
              testID="po-save"
            />
          </Card>

          <Card testID="po-list-card">
            <Text style={styles.cardTitle}>{t("purchaseOrders.history").toUpperCase()}</Text>
            {orders.length === 0 ? (
              <EmptyState icon="clipboard-outline" title={t("purchaseOrders.noOrders")} />
            ) : (
              orders.map((po) => (
                <View key={po.id} style={styles.poCard} testID={`po-${po.id}`}>
                  <View style={styles.poHeader}>
                    <Text style={styles.poNumber}>{po.po_number}</Text>
                    <View style={[styles.statusPill, { backgroundColor: statusColor(po.status) + "22" }]}>
                      <Text style={[styles.statusText, { color: statusColor(po.status) }]}>
                        {t(`purchaseOrders.status.${po.status}` as any)}
                      </Text>
                    </View>
                  </View>
                  {po.vendor_name ? <Text style={styles.poVendor}>{po.vendor_name}</Text> : null}
                  {po.items.map((it, i) => (
                    <Text key={i} style={styles.poItem}>
                      • {it.part_number} {it.name ? `(${it.name})` : ""} × {it.quantity} @ ₹{it.price}
                    </Text>
                  ))}
                  <Text style={styles.poTotal}>{t("purchaseOrders.total")}: ₹{po.total.toFixed(2)}</Text>
                  <Text style={styles.poMeta}>
                    {new Date(po.at).toLocaleString()} • {po.by}
                  </Text>
                  {po.status === "pending" ? (
                    <View style={styles.poActions}>
                      <Button
                        title={t("purchaseOrders.markReceived")}
                        onPress={() => setStatus(po.id, "received")}
                        variant="primary"
                        icon="checkmark-done"
                        style={{ flex: 1 }}
                        testID={`po-receive-${po.id}`}
                      />
                      <Button
                        title={t("purchaseOrders.cancel")}
                        onPress={() => setStatus(po.id, "cancelled")}
                        variant="danger"
                        icon="close-circle"
                        style={{ flex: 1 }}
                        testID={`po-cancel-${po.id}`}
                      />
                    </View>
                  ) : null}
                  {isAdmin ? (
                    <Button
                      title={t("common.delete")}
                      onPress={() => deleteOrder(po.id)}
                      variant="ghost"
                      icon="trash"
                      testID={`po-delete-${po.id}`}
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
  poCard: { paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  poHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  poNumber: { color: colors.onSurface, fontSize: font.base, fontWeight: "800" },
  statusPill: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.sm },
  statusText: { fontSize: font.sm - 1, fontWeight: "800" },
  poVendor: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  poItem: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
  poTotal: { color: colors.onSurface, fontSize: font.base, fontWeight: "800", marginTop: spacing.xs },
  poMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  poActions: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.sm },
});
