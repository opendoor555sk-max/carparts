import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function ChangePassword() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user } = useAuth();
  const { show } = useToast();

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!current.trim()) return show("વર્તમાન password નાખો", "error");
    if (next.length < 6) return show("નવો password ઓછામાં ઓછો 6 અક્ષર", "error");
    if (next !== confirm) return show("Password મેળ ખાતા નથી", "error");
    if (next === current) return show("નવો password જૂના કરતાં અલગ હોવો જોઈએ", "error");
    setSaving(true);
    try {
      await api.post("/auth/change-password", { current_password: current, new_password: next });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      show("Password બદલાઈ ગયો ✓", "success");
      setCurrent("");
      setNext("");
      setConfirm("");
      router.back();
    } catch (e: any) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      show(e?.message || "Password બદલવામાં નિષ્ફળ", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Password બદલો" subtitle={user?.username} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.info}>
            <Ionicons name="lock-closed" size={18} color={colors.brand} />
            <Text style={styles.infoText}>
              તમારો પોતાનો login password અહીં બદલો. બદલ્યા પછી નવો password યાદ રાખો.
            </Text>
          </View>

          <Card>
            <Text style={styles.title}>PASSWORD</Text>
            <Field
              label="વર્તમાન Password"
              value={current}
              onChangeText={setCurrent}
              placeholder="Current password"
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              testID="cp-current"
            />
            <Field
              label="નવો Password (ઓછામાં ઓછો 6 અક્ષર)"
              value={next}
              onChangeText={setNext}
              placeholder="New password"
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              testID="cp-new"
            />
            <Field
              label="નવો Password ફરી લખો"
              value={confirm}
              onChangeText={setConfirm}
              placeholder="Confirm new password"
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              testID="cp-confirm"
            />
            <Button title="Password Update કરો" onPress={submit} loading={saving} icon="save" testID="cp-save" />
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  info: {
    flexDirection: "row",
    gap: spacing.sm,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  infoText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 18 },
  title: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
});
