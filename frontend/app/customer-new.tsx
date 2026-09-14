import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from "react-native";
import { useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { spacing, colors } from "@/src/theme";

export default function CustomerNew() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!name.trim() || !phone.trim()) {
      show(t("customers.errRequired"), "error");
      return;
    }
    setSaving(true);
    try {
      const c = await api.post("/customers", { name: name.trim(), phone: phone.trim(), address: address.trim() });
      show(t("customers.added"), "success");
      router.replace(`/customer/${c.id}` as any);
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("common.saveFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title={t("customers.newTitle")} subtitle={t("customers.subtitle")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}>
          <Card>
            <Field label={t("common.name")} value={name} onChangeText={setName} placeholder={t("customers.namePlaceholder")} testID="customer-name" />
            <Field label={t("common.phone")} value={phone} onChangeText={setPhone} placeholder={t("customers.phonePlaceholder")} keyboardType="phone-pad" testID="customer-phone" />
            <Field label={t("common.addressOptional")} value={address} onChangeText={setAddress} placeholder={t("common.address")} multiline testID="customer-address" />
          </Card>
          <Button title={t("customers.save")} onPress={save} loading={saving} icon="checkmark-circle" testID="customer-save" />
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
});
