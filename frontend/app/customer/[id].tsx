import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, spacing } from "@/src/theme";

type LedgerEntry = {
  id: string;
  type: "sale" | "payment";
  amount: number;
  note?: string;
  part_number?: string;
  running_balance: number;
  by: string;
  at: string;
};

export default function CustomerDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const customerId = id as string;
  const router = useRouter();
  const { show } = useToast();

  const [customer, setCustomer] = useState<any>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [balance, setBalance] = useState(0);
  const [loading, setLoading] = useState(true);

  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  const [payAmount, setPayAmount] = useState("");
  const [payNote, setPayNote] = useState("");
  const [showPayForm, setShowPayForm] = useState(false);
  const [recordingPayment, setRecordingPayment] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get(`/customers/${encodeURIComponent(customerId)}/ledger`);
      setCustomer(res.customer);
      setEntries(res.entries);
      setBalance(res.balance);
      setName(res.customer.name);
      setPhone(res.customer.phone);
      setAddress(res.customer.address || "");
    } catch (e: any) {
      show(e?.message || "Failed to load customer", "error");
    } finally {
      setLoading(false);
    }
  }, [customerId, show]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const saveEdit = async () => {
    if (!name.trim() || !phone.trim()) {
      show("Name and phone are required", "error");
      return;
    }
    setSavingEdit(true);
    try {
      await api.patch(`/customers/${encodeURIComponent(customerId)}`, {
        name: name.trim(),
        phone: phone.trim(),
        address: address.trim(),
      });
      show("Customer updated", "success");
      setEditing(false);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || "Update failed", "error");
    } finally {
      setSavingEdit(false);
    }
  };

  const recordPayment = async () => {
    const amt = parseFloat(payAmount);
    if (!amt || amt <= 0) {
      show("Enter a valid payment amount", "error");
      return;
    }
    setRecordingPayment(true);
    try {
      await api.post(`/customers/${encodeURIComponent(customerId)}/payments`, { amount: amt, note: payNote.trim() });
      show("Payment recorded", "success");
      setPayAmount("");
      setPayNote("");
      setShowPayForm(false);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || "Payment failed", "error");
    } finally {
      setRecordingPayment(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Customer" onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  if (!customer) {
    return (
      <View style={styles.flex}>
        <Header title="Customer" onBack={() => router.back()} />
        <EmptyState icon="person-remove-outline" title="Customer not found" />
      </View>
    );
  }

  const owesMoney = balance > 0;
  const inAdvance = balance < 0;

  return (
    <View style={styles.flex}>
      <Header title={customer.name} subtitle={customer.phone} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xxxl }}>
          <Card testID="customer-balance-card">
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>BALANCE</Text>
              <Button
                title={editing ? "Cancel" : "Edit"}
                onPress={() => setEditing((e) => !e)}
                variant="ghost"
                icon={editing ? "close" : "pencil"}
                testID="customer-edit-toggle"
              />
            </View>
            <Text style={[styles.balanceBig, { color: owesMoney ? colors.error : inAdvance ? colors.success : colors.onSurface }]}>
              ₹{Math.abs(balance).toFixed(2)}
            </Text>
            <Text style={styles.balanceSub}>
              {owesMoney ? "owed to the store" : inAdvance ? "in advance (store owes customer)" : "settled — no dues"}
            </Text>
          </Card>

          {editing ? (
            <Card>
              <Text style={styles.cardTitle}>EDIT CUSTOMER</Text>
              <Field label="Name" value={name} onChangeText={setName} placeholder="Customer name" testID="edit-name" />
              <Field label="Phone" value={phone} onChangeText={setPhone} placeholder="Phone number" keyboardType="phone-pad" testID="edit-phone" />
              <Field label="Address" value={address} onChangeText={setAddress} placeholder="Address" multiline testID="edit-address" />
              <Button title="Save Changes" onPress={saveEdit} loading={savingEdit} icon="checkmark-circle" testID="edit-save" />
            </Card>
          ) : customer.address ? (
            <Card>
              <Text style={styles.cardTitle}>ADDRESS</Text>
              <Text style={styles.addressText}>{customer.address}</Text>
            </Card>
          ) : null}

          <Card>
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>RECORD PAYMENT</Text>
              {!showPayForm ? (
                <Button title="Record Payment" onPress={() => setShowPayForm(true)} icon="cash" testID="show-payment-form" />
              ) : null}
            </View>
            {showPayForm ? (
              <View style={{ marginTop: spacing.sm }}>
                <Field label="Amount received" value={payAmount} onChangeText={setPayAmount} placeholder="₹ 0" keyboardType="numeric" testID="payment-amount" />
                <Field label="Note (optional)" value={payNote} onChangeText={setPayNote} placeholder="e.g. Cash, UPI" testID="payment-note" />
                <Button title="Confirm Payment" onPress={recordPayment} loading={recordingPayment} icon="checkmark-circle" testID="confirm-payment" />
              </View>
            ) : null}
          </Card>

          <Card testID="customer-ledger-card">
            <Text style={styles.cardTitle}>TRANSACTION HISTORY</Text>
            {entries.length === 0 ? (
              <Text style={styles.emptyLedger}>No transactions yet</Text>
            ) : (
              entries.map((e) => (
                <View key={e.id} style={styles.entryRow} testID={`ledger-entry-${e.id}`}>
                  <Ionicons
                    name={e.type === "sale" ? "arrow-up-circle" : "arrow-down-circle"}
                    size={22}
                    color={e.type === "sale" ? colors.error : colors.success}
                  />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.entryTitle}>
                      {e.type === "sale" ? `Sale${e.part_number ? ` — ${e.part_number}` : ""}` : "Payment received"}
                    </Text>
                    {e.note ? <Text style={styles.entryNote}>{e.note}</Text> : null}
                    <Text style={styles.entryMeta}>{new Date(e.at).toLocaleString()} • {e.by}</Text>
                  </View>
                  <View style={{ alignItems: "flex-end" }}>
                    <Text style={[styles.entryAmount, { color: e.type === "sale" ? colors.error : colors.success }]}>
                      {e.type === "sale" ? "+" : "−"}₹{e.amount.toFixed(2)}
                    </Text>
                    <Text style={styles.entryRunning}>Bal: ₹{e.running_balance.toFixed(2)}</Text>
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
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  balanceBig: { fontSize: 40, fontWeight: "900", marginTop: spacing.sm },
  balanceSub: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  addressText: { color: colors.onSurface2, fontSize: font.base },
  emptyLedger: { color: colors.info, textAlign: "center", paddingVertical: spacing.lg },
  entryRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.divider },
  entryTitle: { color: colors.onSurface, fontSize: font.base, fontWeight: "700" },
  entryNote: { color: colors.onSurface2, fontSize: font.sm, marginTop: 1 },
  entryMeta: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  entryAmount: { fontSize: font.base, fontWeight: "900" },
  entryRunning: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
});
