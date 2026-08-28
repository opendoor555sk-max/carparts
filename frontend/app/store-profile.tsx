import { useCallback, useState } from "react";
import { Platform, Pressable, ScrollView, StyleSheet, Text, View, Alert, Linking } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as ImagePicker from "expo-image-picker";

import { api, fileUrl, uploadImage } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

export default function StoreProfile() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { refresh } = useAuth();
  const { show } = useToast();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState("");
  const [gst, setGst] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [bank, setBank] = useState("");
  const [logoPath, setLogoPath] = useState("");
  const [logoDisplay, setLogoDisplay] = useState("");

  const load = useCallback(async () => {
    try {
      const s = await api.get("/store/profile");
      setName(s.name || "");
      setGst(s.gst || "");
      setPhone(s.phone || "");
      setAddress(s.address || "");
      setBank(s.bank || "");
      if (s.logo_path) {
        setLogoPath(s.logo_path);
        setLogoDisplay(await fileUrl(s.logo_path));
      }
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [show]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const pickLogo = async () => {
    let perm = await ImagePicker.getMediaLibraryPermissionsAsync();
    if (!perm.granted && perm.canAskAgain) perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert("Gallery access needed", "Allow gallery to pick your store logo.", [
        { text: "Cancel", style: "cancel" },
        { text: "Open Settings", onPress: () => Linking.openSettings() },
      ]);
      return;
    }
    const res = await ImagePicker.launchImageLibraryAsync({ quality: 0.7 });
    if (!res.canceled && res.assets?.[0]?.uri) {
      try {
        const { path } = await uploadImage(res.assets[0].uri, "logo.jpg");
        setLogoPath(path);
        setLogoDisplay(await fileUrl(path));
        show("Logo uploaded", "success");
      } catch {
        show("Logo upload failed", "error");
      }
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      await api.post("/store/profile", {
        name: name.trim(),
        gst: gst.trim(),
        phone: phone.trim(),
        address: address.trim(),
        bank: bank.trim(),
        logo_path: logoPath,
      });
      await refresh();
      show("Store profile saved", "success");
      router.back();
    } catch (e: any) {
      show(e?.message || "Save failed", "error");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Store Profile" onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title="Store Profile / Branding" onBack={() => router.back()} />
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 40, gap: spacing.md }}>
        <Card>
          <Text style={styles.cardTitle}>STORE LOGO</Text>
          <View style={styles.logoRow}>
            {logoDisplay ? (
              <Image source={{ uri: logoDisplay }} style={styles.logo} contentFit="contain" />
            ) : (
              <View style={[styles.logo, styles.logoEmpty]}>
                <Ionicons name="image" size={28} color={colors.info} />
              </View>
            )}
            <Button title="Choose Logo" onPress={pickLogo} variant="secondary" icon="cloud-upload" testID="pick-logo" style={{ flex: 1 }} />
          </View>
          <Text style={styles.hint}>Shown on every printed receipt / report.</Text>
        </Card>

        <Card>
          <Field label="Store Name" value={name} onChangeText={setName} placeholder="Store name" testID="sp-name" />
          <Field label="GST Number" value={gst} onChangeText={setGst} placeholder="e.g. 24ABCDE1234F1Z5" autoCapitalize="characters" testID="sp-gst" />
          <Field label="Phone" value={phone} onChangeText={setPhone} placeholder="e.g. +91 98xxxxxxxx" keyboardType={Platform.OS === "web" ? "default" : "phone-pad"} testID="sp-phone" />
          <Field label="Address" value={address} onChangeText={setAddress} placeholder="Shop address" testID="sp-address" />
          <Field label="Bank Details" value={bank} onChangeText={setBank} placeholder="Bank name, A/C no, IFSC" testID="sp-bank" />
        </Card>

        <Button title="Save Profile" onPress={save} loading={saving} icon="save" testID="save-profile" />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  logoRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  logo: { width: 72, height: 72, borderRadius: radius.sm, backgroundColor: colors.surface3 },
  logoEmpty: { alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: colors.border },
  hint: { color: colors.info, fontSize: font.sm, marginTop: spacing.sm },
});
