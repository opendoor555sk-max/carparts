import { useEffect, useState } from "react";
import { KeyboardAvoidingView, Linking, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Settings() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { refresh } = useAuth();
  const { show } = useToast();
  const [apiKey, setApiKey] = useState("");
  const [cx, setCx] = useState("");
  const [hasKey, setHasKey] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const s = await api.get("/auth/settings");
        setCx(s.google_cx || "");
        setHasKey(!!s.has_google_key);
      } catch {}
    })();
  }, []);

  const save = async () => {
    if (!apiKey.trim() && !hasKey) {
      show("API Key નાખો", "error");
      return;
    }
    setSaving(true);
    try {
      const payload: any = { google_cx: cx.trim() };
      if (apiKey.trim()) payload.google_api_key = apiKey.trim();
      const r = await api.post("/auth/settings", payload);
      setHasKey(!!r.has_google_key);
      setApiKey("");
      await refresh();
      show("Google API settings saved", "success");
    } catch (e: any) {
      show(e?.message || "Failed", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.flex}>
      <Header title="Google Search Setup" subtitle="BYO-Key — free 100/day" onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }} keyboardShouldPersistTaps="handled">
          <View style={styles.info}>
            <Ionicons name="key" size={18} color={colors.brand} />
            <Text style={styles.infoText}>
              તમારી પોતાની Google key વાપરો → દરરોજ 100 free search, host credit ZERO. Key host પર secure save થાય.
            </Text>
          </View>

          <Card>
            <Text style={styles.title}>YOUR GOOGLE CREDENTIALS</Text>
            <Field
              label={hasKey ? "API KEY (saved — નવી નાખો તો બદલાશે)" : "GOOGLE API KEY"}
              value={apiKey}
              onChangeText={setApiKey}
              placeholder={hasKey ? "•••••••• (already set)" : "AIza..."}
              autoCapitalize="none"
              autoCorrect={false}
              secureTextEntry
              testID="google-api-key"
            />
            <Field
              label="SEARCH ENGINE ID (CX)"
              value={cx}
              onChangeText={setCx}
              placeholder="e.g. a1b2c3d4e5f6g7h8i"
              autoCapitalize="none"
              autoCorrect={false}
              testID="google-cx"
            />
            <Button title="Save Settings" onPress={save} loading={saving} icon="save" testID="save-settings" />
            {hasKey ? (
              <View style={styles.okRow}>
                <Ionicons name="checkmark-circle" size={16} color={colors.success} />
                <Text style={styles.okText}>Google key configured ✓</Text>
              </View>
            ) : null}
          </Card>

          <Card>
            <Text style={styles.title}>Key કેવી રીતે લેવી (free, card નહીં)</Text>
            <Step n="1" t="console.cloud.google.com → નવું project બનાવો" />
            <Step n="2" t="'Custom Search API' enable કરો → Credentials → API Key બનાવો" />
            <Step n="3" t="programmablesearchengine.google.com → નવું search engine (Search entire web) → Search Engine ID (CX) મળશे" />
            <Step n="4" t="ઉપર બંને paste કરી Save કરો" />
            <Button
              title="Google Console ખોલો"
              onPress={() => Linking.openURL("https://console.cloud.google.com/apis/library/customsearch.googleapis.com")}
              variant="secondary"
              icon="open"
              testID="open-console"
              style={{ marginTop: spacing.sm }}
            />
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

function Step({ n, t }: { n: string; t: string }) {
  return (
    <View style={styles.step}>
      <View style={styles.stepNum}>
        <Text style={styles.stepNumText}>{n}</Text>
      </View>
      <Text style={styles.stepText}>{t}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  info: { flexDirection: "row", gap: spacing.sm, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.md },
  infoText: { color: colors.onSurface3, fontSize: font.sm, flex: 1, lineHeight: 18 },
  title: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  okRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.md },
  okText: { color: colors.success, fontWeight: "700", fontSize: font.base },
  step: { flexDirection: "row", gap: spacing.sm, alignItems: "center", marginBottom: spacing.sm },
  stepNum: { width: 24, height: 24, borderRadius: 12, backgroundColor: colors.brandFaint, alignItems: "center", justifyContent: "center" },
  stepNumText: { color: colors.brand, fontWeight: "800", fontSize: font.sm },
  stepText: { color: colors.onSurface2, fontSize: font.base, flex: 1 },
});
