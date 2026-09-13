import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from "react-native";
import { useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { spacing, colors } from "@/src/theme";

export default function CustomerNew() {
  const router = useRouter();
  const { show } = useToast();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!name.trim() || !phone.trim()) {
      show("Name and phone are required", "error");
      return;
    }
    setSaving(true);
    try {
      const c = await api.post("/customers", { name: name.trim(), phone: phone.trim(), address: address.trim() });
      show("Customer added", "success");
      router.replace(`/customer/${c.id}` as any);
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || "Save failed", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="New Customer" subtitle="Grahak Khata" onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}>
          <Card>
            <Field label="Name" value={name} onChangeText={setName} placeholder="Customer name" testID="customer-name" />
            <Field label="Phone" value={phone} onChangeText={setPhone} placeholder="10-digit phone number" keyboardType="phone-pad" testID="customer-phone" />
            <Field label="Address (optional)" value={address} onChangeText={setAddress} placeholder="Address" multiline testID="customer-address" />
          </Card>
          <Button title="Save Customer" onPress={save} loading={saving} icon="checkmark-circle" testID="customer-save" />
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
});
