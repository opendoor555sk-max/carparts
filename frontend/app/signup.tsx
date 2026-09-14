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
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Field } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function SignUp() {
  const { register } = useAuth();
  const { t } = useLanguage();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [storeName, setStoreName] = useState("");
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [contact, setContact] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async () => {
    setErr("");
    if (!storeName.trim() || !username.trim() || !password) {
      setErr(t("signup.errRequired"));
      return;
    }
    if (!contact.trim()) {
      setErr(t("signup.errContact"));
      return;
    }
    if (password.length < 6) {
      setErr(t("signup.errPasswordLen"));
      return;
    }
    setLoading(true);
    try {
      await register({
        store_name: storeName.trim(),
        name: name.trim(),
        username: username.trim(),
        password,
        contact: contact.trim(),
      });
      router.replace("/(tabs)");
    } catch (e: any) {
      setErr(e?.message || t("signup.errFailed"));
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
          <Text style={styles.title}>{t("signup.title")}</Text>
          <Text style={styles.subtitle}>{t("signup.subtitle")}</Text>
        </View>

        <View style={styles.form}>
          <Field
            label={t("signup.storeName").toUpperCase()}
            value={storeName}
            onChangeText={setStoreName}
            placeholder={t("signup.storeNamePlaceholder")}
            testID="signup-store"
          />
          <Field
            label={t("signup.yourName").toUpperCase()}
            value={name}
            onChangeText={setName}
            placeholder={t("signup.yourNamePlaceholder")}
            testID="signup-name"
          />
          <Field
            label={t("signup.contact").toUpperCase()}
            value={contact}
            onChangeText={setContact}
            placeholder="e.g. +91 98xxxxxxxx"
            keyboardType="phone-pad"
            testID="signup-contact"
          />
          <Field
            label={t("signup.usernameLabel")}
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder={t("login.username")}
            testID="signup-username"
          />
          <View>
            <Field
              label={t("login.password").toUpperCase()}
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPw}
              placeholder={t("signup.passwordPlaceholder")}
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
            title={t("signup.submit")}
            onPress={onSubmit}
            loading={loading}
            icon="add-circle"
            testID="signup-submit"
            style={{ marginTop: spacing.sm }}
          />
        </View>

        <Pressable onPress={() => router.replace("/login")} style={styles.linkRow} testID="go-login">
          <Text style={styles.linkText}>{t("signup.haveAccount")} </Text>
          <Text style={[styles.linkText, { color: colors.brand, fontWeight: "800" }]}>{t("login.signIn")}</Text>
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
