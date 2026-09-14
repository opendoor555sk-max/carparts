import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from "react-native";
import { useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { spacing, colors } from "@/src/theme";

export default function VendorNew() {
  const router = useRouter();
  const { show } = useToast();
  const { t } = useLanguage();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!name.trim() || !phone.trim()) {
      show(t("customers.errRequired"), "error");
      return;
    }
    setSaving(true);
    try {
      const v = await api.post("/vendors", { name: name.trim(), phone: phone.trim(), address: address.trim(), notes: notes.trim() });
      show(t("vendors.added"), "success");
      router.replace(`/vendor/${v.id}` as any);
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("common.saveFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title={t("vendors.newTitle")} subtitle={t("vendors.title")} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}>
          <Card>
            <Field label={t("common.name")} value={name} onChangeText={setName} placeholder={t("vendors.namePlaceholder")} testID="vendor-name" />
            <Field label={t("common.phone")} value={phone} onChangeText={setPhone} placeholder={t("customers.phonePlaceholder")} keyboardType="phone-pad" testID="vendor-phone" />
            <Field label={t("common.addressOptional")} value={address} onChangeText={setAddress} placeholder={t("common.address")} multiline testID="vendor-address" />
            <Field label={t("vendors.notesOptional")} value={notes} onChangeText={setNotes} placeholder="e.g. Electrical parts, OEM Maruti" multiline testID="vendor-notes" />
          </Card>
          <Button title={t("vendors.save")} onPress={save} loading={saving} icon="checkmark-circle" testID="vendor-save" />
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
});
