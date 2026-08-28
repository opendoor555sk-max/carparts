import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/context/AuthContext";
import { Button, Field } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function SignUp() {
  const { register } = useAuth();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [storeName, setStoreName] = useState("");
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async () => {
    setErr("");
    if (!storeName.trim() || !username.trim() || !password) {
      setErr("Store name, username અને password જરૂરી છે");
      return;
    }
    if (password.length < 6) {
      setErr("Password ઓછામાં ઓછો 6 અક્ષર હોવો જોઈએ");
      return;
    }
    setLoading(true);
    try {
      await register({
        store_name: storeName.trim(),
        name: name.trim(),
        username: username.trim(),
        password,
      });
      router.replace("/(tabs)");
    } catch (e: any) {
      setErr(e?.message || "Sign up failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView
        contentContainerStyle={[styles.container, { paddingTop: insets.top + spacing.xxl }]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.logoWrap}>
          <View style={styles.logoBox}>
            <Ionicons name="storefront" size={38} color={colors.brand} />
          </View>
          <Text style={styles.title}>નવું Store બનાવો</Text>
          <Text style={styles.subtitle}>તમારું પોતાનું Auto Parts Store શરૂ કરો</Text>
        </View>

        <View style={styles.form}>
          <Field
            label="STORE NAME (દુકાનનું નામ)"
            value={storeName}
            onChangeText={setStoreName}
            placeholder="e.g. Raja Auto Parts"
            testID="signup-store"
          />
          <Field
            label="YOUR NAME (તમારું નામ)"
            value={name}
            onChangeText={setName}
            placeholder="Owner name"
            testID="signup-name"
          />
          <Field
            label="USERNAME (login માટે)"
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="username"
            testID="signup-username"
          />
          <View>
            <Field
              label="PASSWORD"
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPw}
              placeholder="ઓછામાં ઓછો 6 અક્ષર"
              testID="signup-password"
              onSubmitEditing={onSubmit}
              returnKeyType="go"
            />
            <Pressable style={styles.eye} onPress={() => setShowPw((s) => !s)} hitSlop={12}>
              <Ionicons name={showPw ? "eye-off" : "eye"} size={20} color={colors.info} />
            </Pressable>
          </View>

          {err ? (
            <View style={styles.errBanner} testID="signup-error">
              <Ionicons name="warning" size={16} color={colors.onError} />
              <Text style={styles.errText}>{err}</Text>
            </View>
          ) : null}

          <Button
            title="Create Store & Sign In"
            onPress={onSubmit}
            loading={loading}
            icon="add-circle"
            testID="signup-submit"
            style={{ marginTop: spacing.sm }}
          />
        </View>

        <Pressable onPress={() => router.replace("/login")} style={styles.linkRow} testID="go-login">
          <Text style={styles.linkText}>પહેલેથી account છે? </Text>
          <Text style={[styles.linkText, { color: colors.brand, fontWeight: "800" }]}>Sign In</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  container: { paddingHorizontal: spacing.xl, paddingBottom: spacing.xxxl, minHeight: "100%" },
  logoWrap: { alignItems: "center", marginBottom: spacing.xl },
  logoBox: {
    width: 84,
    height: 84,
    borderRadius: radius.lg,
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.lg,
  },
  title: { color: colors.onSurface, fontSize: font.xxl, fontWeight: "800", textAlign: "center" },
  subtitle: { color: colors.brand, fontSize: font.sm, fontWeight: "700", marginTop: spacing.xs },
  form: {
    backgroundColor: colors.surface2,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.xl,
  },
  eye: { position: "absolute", right: spacing.md, top: 34 },
  errBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.error,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.sm,
  },
  errText: { color: colors.onError, fontSize: font.base, fontWeight: "700", flex: 1 },
  linkRow: { flexDirection: "row", justifyContent: "center", marginTop: spacing.xl },
  linkText: { color: colors.info, fontSize: font.base },
});
