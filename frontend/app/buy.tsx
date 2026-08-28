import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";
import * as Location from "expo-location";
import * as ImagePicker from "expo-image-picker";

import { api, fileUrl, uploadImage } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, FilterChip, Header, LimitBar, Loading, StatusChip } from "@/src/components/ui";
import { printReceipt, brandingFromUser } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

const COMPANIES = ["All", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda", "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet"];

const CONDITIONS = ["Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown"];

export default function Buy() {
  const { pn, company = "All" } = useLocalSearchParams<{ pn: string; company: string }>();
  const partNumber = decodeURIComponent(pn as string);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { can, user } = useAuth();
  const { show } = useToast();

  const [info, setInfo] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [searching, setSearching] = useState(false);
  const [gps, setGps] = useState("");

  useEffect(() => {
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== "granted") return;
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        setGps(`${loc.coords.latitude.toFixed(5)}, ${loc.coords.longitude.toFixed(5)}`);
      } catch {}
    })();
  }, []);

  const [condition, setCondition] = useState("Working");
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [buyCompany, setBuyCompany] = useState(company);
  const [vehicles, setVehicles] = useState("");
  const [variant, setVariant] = useState("");
  const [rack, setRack] = useState("");
  const [shelf, setShelf] = useState("");
  const [box, setBox] = useState("");
  const [position, setPosition] = useState("");
  const [price, setPrice] = useState("");
  const [override, setOverride] = useState(false);
  const [photos, setPhotos] = useState<{ path: string; display: string }[]>([]);
  const [uploading, setUploading] = useState(false);

  const doUpload = async (uri: string) => {
    setUploading(true);
    try {
      const { path } = await uploadImage(uri);
      const display = await fileUrl(path);
      setPhotos((prev) => [...prev, { path, display }]);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch {
      show("Photo upload failed", "error");
    } finally {
      setUploading(false);
    }
  };

  const takePhoto = async () => {
    if (photos.length >= 6) return show("Maximum 6 photos", "info");
    let perm = await ImagePicker.getCameraPermissionsAsync();
    if (!perm.granted && perm.canAskAgain) perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) {
      Alert.alert("Camera needed", "Allow camera to take part photos.", [
        { text: "Cancel", style: "cancel" },
        { text: "Open Settings", onPress: () => Linking.openSettings() },
      ]);
      return;
    }
    const res = await ImagePicker.launchCameraAsync({ quality: 0.6 });
    if (!res.canceled && res.assets?.[0]?.uri) await doUpload(res.assets[0].uri);
  };

  const pickGallery = async () => {
    if (photos.length >= 6) return show("Maximum 6 photos", "info");
    let perm = await ImagePicker.getMediaLibraryPermissionsAsync();
    if (!perm.granted && perm.canAskAgain) perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert("Gallery needed", "Allow gallery to add photos.", [
        { text: "Cancel", style: "cancel" },
        { text: "Open Settings", onPress: () => Linking.openSettings() },
      ]);
      return;
    }
    const remaining = 6 - photos.length;
    const res = await ImagePicker.launchImageLibraryAsync({
      quality: 0.6,
      allowsMultipleSelection: true,
      selectionLimit: remaining,
    });
    if (!res.canceled) {
      for (const a of res.assets || []) {
        if (a.uri) await doUpload(a.uri);
      }
    }
  };

  const removePhoto = (path: string) => setPhotos((prev) => prev.filter((p) => p.path !== path));

  const load = useCallback(async () => {
    try {
      const res = await api.get(`/search?q=${encodeURIComponent(partNumber)}`);
      setInfo(res);
      if (res.part?.name) setName(res.part.name);
      if (res.part?.category) setCategory(res.part.category);
      if (res.part?.variant) setVariant(res.part.variant);
      if (res.part?.compatible_vehicles?.length) setVehicles(res.part.compatible_vehicles.join(", "));
    } catch (e: any) {
      show(e?.message || "Load failed", "error");
    } finally {
      setLoading(false);
    }
  }, [partNumber]);

  useEffect(() => {
    load();
  }, [load]);

  const limit = info?.limit;
  const isStop = limit?.limit_enabled && limit?.remaining !== null && limit?.remaining <= 0;
  const isWarn = limit?.status === "WARNING";

  const googleAutofill = async () => {
    setSearching(true);
    try {
      const r = await api.post("/search/web", { part_number: partNumber, company });
      if (r.name) setName(r.name);
      if (r.models?.length) setVehicles(r.models.join(", "));
      if (r.variants?.length) setVariant(r.variants.join(", "));
      show(r.cached ? "Autofilled from library (100% verified)" : `Autofilled — ${r.result_count || 0} web results`, "success");
    } catch (e: any) {
      const d = e?.detail;
      if (d?.code === "NO_KEY") {
        show("No Google key — add it in Settings", "error");
        router.push("/settings" as any);
      } else {
        show(d?.message || e?.message || "Search failed", "error");
      }
    } finally {
      setSearching(false);
    }
  };

  const submit = async () => {
    if (isStop && !override) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      show("Limit reached — toggle Override (Admin)", "error");
      return;
    }
    setSubmitting(true);
    try {
      await api.post("/buy", {
        part_number: partNumber,
        company: buyCompany,
        name,
        category,
        compatible_vehicles: vehicles.split(",").map((s) => s.trim()).filter(Boolean),
        variant,
        condition,
        location: { rack, shelf, box, position, gps },
        price: price ? parseFloat(price) : null,
        photos: photos.map((p) => p.path),
        override,
      });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      show(`Bought — stock updated`, "success");
      router.replace(`/part/${encodeURIComponent(partNumber)}` as any);
    } catch (e: any) {
      const d = e?.detail;
      if (d?.code === "LIMIT_REACHED") {
        show("DO NOT BUY — limit reached", "error");
        setInfo((prev: any) => ({ ...prev, limit: d.limit }));
      } else {
        show(e?.message || "Buy failed", "error");
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Buy" onBack={() => router.back()} />
        <Loading text="Calculating limit…" />
      </View>
    );
  }

  return (
    <View style={[styles.flex, isStop && !override && styles.stopBorder]}>
      <Header title="BUY" subtitle={partNumber} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView
          contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 100, gap: spacing.md }}
          keyboardShouldPersistTaps="handled"
        >
          {/* Limit meter */}
          <Card testID="buy-limit-card">
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>PURCHASE LIMIT</Text>
              <StatusChip status={info?.status} />
            </View>
            <LimitBar existing={limit?.existing_stock ?? 0} allowed={limit?.allowed_limit ?? null} />
            {limit?.limit_enabled ? (
              <Text style={styles.remainText}>
                Remaining allowed:{" "}
                <Text style={{ color: isStop ? colors.error : colors.brand, fontWeight: "800" }}>
                  {limit.remaining}
                </Text>
              </Text>
            ) : null}
          </Card>

          {isStop && !override ? (
            <View style={styles.doNotBuy} testID="do-not-buy">
              <Ionicons name="hand-left" size={22} color={colors.onError} />
              <Text style={styles.doNotBuyText}>DO NOT BUY</Text>
              <Text style={styles.doNotBuySub}>Purchase limit reached</Text>
            </View>
          ) : isWarn ? (
            <View style={styles.warnBanner}>
              <Ionicons name="warning" size={18} color={colors.onWarning} />
              <Text style={styles.warnText}>WARNING — near limit</Text>
            </View>
          ) : null}

          {/* Condition */}
          <Card>
            <Text style={styles.cardTitle}>CONDITION</Text>
            <View style={styles.condGrid}>
              {CONDITIONS.map((c) => (
                <Pressable
                  key={c}
                  onPress={() => setCondition(c)}
                  style={[
                    styles.condChip,
                    { backgroundColor: condition === c ? colors.brand : colors.surface, borderColor: condition === c ? colors.brand : colors.border },
                  ]}
                  testID={`buy-cond-${c}`}
                >
                  <Text style={{ color: condition === c ? colors.onBrand : colors.onSurface2, fontWeight: "700", fontSize: font.sm }}>
                    {c}
                  </Text>
                </Pressable>
              ))}
            </View>
          </Card>

          {/* Part & compatibility (auto-filled, saved under the part number on purchase) */}
          <Card testID="buy-compat-card">
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>PART & COMPATIBILITY</Text>
              {info?.part ? <StatusChip status={info.part.verification_status} /> : null}
            </View>
            <Button
              title="🔍 Google Autofill (your key)"
              onPress={googleAutofill}
              loading={searching}
              variant="secondary"
              icon="search"
              testID="google-autofill"
              style={{ marginBottom: spacing.md }}
            />
            <Field label="Name" value={name} onChangeText={setName} placeholder="Part name" testID="buy-name" />
            <Text style={styles.pickLabel}>COMPANY</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.pickRow}>
              {COMPANIES.map((c) => (
                <FilterChip key={c} label={c} active={buyCompany === c} onPress={() => setBuyCompany(c)} testID={`buy-co-${c}`} />
              ))}
            </ScrollView>
            <Field label="Category" value={category} onChangeText={setCategory} placeholder="Category" testID="buy-category" />
            <Field
              label="Compatible Vehicles (comma separated)"
              value={vehicles}
              onChangeText={setVehicles}
              placeholder="Hyundai Creta, Kia Seltos"
              testID="buy-vehicles"
            />
            <Field label="Variant" value={variant} onChangeText={setVariant} placeholder="e.g. HTC Diesel" testID="buy-variant" />
            <View style={styles.compatHint}>
              <Ionicons name="save" size={13} color={colors.brand} />
              <Text style={styles.compatHintText}>
                Company ({buyCompany}) + these details will auto-save under this part number with the purchase
              </Text>
            </View>
          </Card>

          {/* Location */}
          <Card>
            <Text style={styles.cardTitle}>LOCATION (Rack → Shelf → Box → Position)</Text>
            <View style={styles.locGrid}>
              <View style={styles.locItem}>
                <Field label="Rack" value={rack} onChangeText={setRack} placeholder="R1" testID="loc-rack" />
              </View>
              <View style={styles.locItem}>
                <Field label="Shelf" value={shelf} onChangeText={setShelf} placeholder="S2" testID="loc-shelf" />
              </View>
              <View style={styles.locItem}>
                <Field label="Box" value={box} onChangeText={setBox} placeholder="B3" testID="loc-box" />
              </View>
              <View style={styles.locItem}>
                <Field label="Position" value={position} onChangeText={setPosition} placeholder="P4" testID="loc-position" />
              </View>
            </View>
            <View style={styles.gpsRow} testID="gps-synced">
              <Ionicons name={gps ? "location" : "location-outline"} size={14} color={gps ? colors.success : colors.info} />
              <Text style={[styles.gpsText, { color: gps ? colors.success : colors.info }]}>
                {gps ? `GPS synced: ${gps}` : "GPS location fetching…"}
              </Text>
            </View>
          </Card>

          {/* Part Photos (6-side) */}
          <Card testID="buy-photos-card">
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>PART PHOTOS (6 sides)</Text>
              <Text style={styles.photoCount}>{photos.length}/6</Text>
            </View>
            <View style={styles.photoGrid}>
              {photos.map((p) => (
                <View key={p.path} style={styles.thumbWrap}>
                  <Image source={{ uri: p.display }} style={styles.thumb} contentFit="cover" />
                  <Pressable style={styles.thumbDel} onPress={() => removePhoto(p.path)} testID={`del-photo-${p.path}`}>
                    <Ionicons name="close" size={14} color={colors.onError} />
                  </Pressable>
                </View>
              ))}
              {photos.length < 6 ? (
                <Pressable style={styles.addPhoto} onPress={takePhoto} disabled={uploading} testID="buy-take-photo">
                  <Ionicons name={uploading ? "hourglass" : "camera"} size={22} color={colors.brand} />
                  <Text style={styles.addPhotoText}>Camera</Text>
                </Pressable>
              ) : null}
              {photos.length < 6 ? (
                <Pressable style={styles.addPhoto} onPress={pickGallery} disabled={uploading} testID="buy-pick-gallery">
                  <Ionicons name="images" size={22} color={colors.brand} />
                  <Text style={styles.addPhotoText}>Gallery</Text>
                </Pressable>
              ) : null}
            </View>
            <Text style={styles.photoHint}>Front, Back, Left, Right, Top, Bottom — add a photo of each side</Text>
          </Card>

          {/* Admin-only price */}
          {can("view_price") ? (
            <Card>
              <View style={styles.rowBetween}>
                <Text style={styles.cardTitle}>PURCHASE PRICE</Text>
                <View style={styles.adminTag}>
                  <Ionicons name="lock-closed" size={11} color={colors.brand} />
                  <Text style={styles.adminTagText}>Admin only</Text>
                </View>
              </View>
              <Field
                value={price}
                onChangeText={setPrice}
                placeholder="₹ 0"
                keyboardType="numeric"
                testID="buy-price"
              />
            </Card>
          ) : null}

          {/* Override */}
          {can("manage_limits") ? (
            <Card>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.overrideTitle}>Admin Override</Text>
                  <Text style={styles.dim}>Buy ignoring the limit</Text>
                </View>
                <Switch
                  value={override}
                  onValueChange={setOverride}
                  trackColor={{ true: colors.brand, false: colors.surface3 }}
                  thumbColor={colors.onSurface}
                  testID="override-switch"
                />
              </View>
            </Card>
          ) : null}
        </ScrollView>

        <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
          <Button
            title="Print Slip"
            onPress={async () =>
              printReceipt(await brandingFromUser(user), "BUY", {
                part_number: partNumber,
                name,
                condition,
                location: { rack, shelf, box, position },
                price: price || null,
                by: user?.name,
              })
            }
            variant="secondary"
            icon="print"
            testID="print-buy"
            style={{ marginBottom: spacing.sm }}
          />
          <Button
            title={isStop && !override ? "BLOCKED — Limit Reached" : "Confirm Buy (Stock +1)"}
            onPress={submit}
            loading={submitting}
            disabled={isStop && !override}
            variant={isStop && !override ? "danger" : "primary"}
            icon="checkmark-circle"
            testID="confirm-buy"
          />
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  stopBorder: { borderWidth: 3, borderColor: colors.error },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  remainText: { color: colors.onSurface2, fontSize: font.base, marginTop: spacing.md },
  doNotBuy: {
    alignItems: "center",
    gap: spacing.xs,
    backgroundColor: colors.error,
    borderRadius: radius.md,
    padding: spacing.lg,
  },
  doNotBuyText: { color: colors.onError, fontSize: font.xxl, fontWeight: "800", letterSpacing: 1 },
  doNotBuySub: { color: colors.onError, fontSize: font.base },
  warnBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.warning,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  warnText: { color: colors.onWarning, fontWeight: "800", fontSize: font.base },
  condGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  condChip: { paddingHorizontal: spacing.md, paddingVertical: spacing.sm, borderRadius: radius.pill, borderWidth: 1 },
  locGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  locItem: { width: "48%" },
  adminTag: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.brandFaint, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 3 },
  adminTagText: { color: colors.brand, fontSize: font.sm - 1, fontWeight: "700" },
  overrideTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  dim: { color: colors.info, fontSize: font.sm },
  bar: { backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
  compatHint: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.xs },
  compatHintText: { color: colors.info, fontSize: font.sm, flex: 1 },
  pickLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 0.5, marginTop: spacing.xs, marginBottom: spacing.xs },
  pickRow: { gap: spacing.sm, paddingVertical: spacing.xs, paddingRight: spacing.md },
  gpsRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs, marginTop: spacing.sm },
  gpsText: { fontSize: font.sm, fontWeight: "700" },
  photoCount: { color: colors.info, fontSize: font.sm, fontWeight: "800" },
  photoGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  thumbWrap: { width: 72, height: 72, borderRadius: radius.sm, overflow: "hidden", position: "relative" },
  thumb: { width: "100%", height: "100%", backgroundColor: colors.surface3 },
  thumbDel: { position: "absolute", top: 2, right: 2, width: 22, height: 22, borderRadius: 11, backgroundColor: colors.error, alignItems: "center", justifyContent: "center" },
  addPhoto: { width: 72, height: 72, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.brandFaint, backgroundColor: colors.surface, alignItems: "center", justifyContent: "center", gap: 2 },
  addPhotoText: { color: colors.brand, fontSize: font.sm - 1, fontWeight: "700" },
  photoHint: { color: colors.info, fontSize: font.sm, marginTop: spacing.sm },
});
