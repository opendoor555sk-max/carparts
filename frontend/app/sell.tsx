import { useCallback, useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Haptics from "expo-haptics";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { Button, Card, Field, Header, Loading, StatusChip, EmptyState } from "@/src/components/ui";
import { printReceipt, brandingFromUser } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";

export default function Sell() {
  const { pn } = useLocalSearchParams<{ pn: string }>();
  const partNumber = decodeURIComponent(pn as string);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { can, user } = useAuth();
  const { show } = useToast();

  const [part, setPart] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [selectedUnit, setSelectedUnit] = useState<string | null>(null);
  const [price, setPrice] = useState("");
  const [buyer, setBuyer] = useState("");

  const load = useCallback(async () => {
    try {
      const full = await api.get(`/parts/${encodeURIComponent(partNumber)}`);
      setPart(full);
      if (full.units?.length) setSelectedUnit(full.units[0].id);
    } catch {
      setPart(null);
    } finally {
      setLoading(false);
    }
  }, [partNumber]);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async () => {
    setSubmitting(true);
    try {
      await api.post("/sell", {
        part_number: partNumber,
        unit_id: selectedUnit,
        price: price ? parseFloat(price) : null,
        buyer,
      });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      show("Sold — stock ઘટ્યું", "success");
      router.replace(`/part/${encodeURIComponent(partNumber)}` as any);
    } catch (e: any) {
      const d = e?.detail;
      show(d?.message || e?.message || "Sell failed", "error");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title="Sell" onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  const units = part?.units || [];
  const hasStock = units.length > 0;

  return (
    <View style={styles.flex}>
      <Header title="SELL — વેચો" subtitle={partNumber} onBack={() => router.back()} />
      {!hasStock ? (
        <EmptyState
          icon="close-circle-outline"
          title="કોઈ stock નથી"
          subtitle="આ part number stock માં નથી — sell ન થાય"
          action={<Button title="Back" onPress={() => router.back()} variant="secondary" testID="sell-back" />}
        />
      ) : (
        <>
          <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: insets.bottom + 100, gap: spacing.md }}>
            <Card>
              <View style={styles.rowBetween}>
                <Text style={styles.cardTitle}>AVAILABLE STOCK</Text>
                <Text style={styles.count}>{units.length} units</Text>
              </View>
              {units.map((u: any) => (
                <Pressable
                  key={u.id}
                  onPress={() => setSelectedUnit(u.id)}
                  style={[styles.unit, selectedUnit === u.id && styles.unitActive]}
                  testID={`sell-unit-${u.id}`}
                >
                  <Ionicons
                    name={selectedUnit === u.id ? "radio-button-on" : "radio-button-off"}
                    size={20}
                    color={selectedUnit === u.id ? colors.brand : colors.info}
                  />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.unitLoc}>
                      {[u.location?.rack, u.location?.shelf, u.location?.box, u.location?.position]
                        .filter(Boolean)
                        .join(" → ") || "No location"}
                    </Text>
                  </View>
                  <StatusChip status={u.condition} />
                </Pressable>
              ))}
            </Card>

            {can("view_price") ? (
              <Card>
                <View style={styles.rowBetween}>
                  <Text style={styles.cardTitle}>SALE PRICE</Text>
                  <View style={styles.adminTag}>
                    <Ionicons name="lock-closed" size={11} color={colors.brand} />
                    <Text style={styles.adminTagText}>Admin only</Text>
                  </View>
                </View>
                <Field value={price} onChangeText={setPrice} placeholder="₹ 0" keyboardType="numeric" testID="sell-price" />
              </Card>
            ) : null}

            <Card>
              <Text style={styles.cardTitle}>BUYER (optional)</Text>
              <Field value={buyer} onChangeText={setBuyer} placeholder="Buyer name" testID="sell-buyer" />
            </Card>
          </ScrollView>

          <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
            <Button
              title="Print Bill"
              onPress={async () => {
                const u = units.find((x: any) => x.id === selectedUnit) || units[0];
                printReceipt(await brandingFromUser(user), "SELL", {
                  part_number: partNumber,
                  name: part?.name,
                  condition: u?.condition,
                  location: u?.location,
                  price: price || null,
                  buyer,
                  by: user?.name,
                });
              }}
              variant="secondary"
              icon="print"
              testID="print-sell"
              style={{ marginBottom: spacing.sm }}
            />
            <Button title="Confirm Sell (Stock −1)" onPress={submit} loading={submitting} icon="cash" testID="confirm-sell" />
          </View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.md },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  count: { color: colors.brand, fontWeight: "800", fontSize: font.base },
  unit: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
  },
  unitActive: {},
  unitLoc: { color: colors.onSurface2, fontSize: font.base },
  adminTag: { flexDirection: "row", alignItems: "center", gap: 4, backgroundColor: colors.brandFaint, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 3 },
  adminTagText: { color: colors.brand, fontSize: font.sm - 1, fontWeight: "700" },
  bar: { backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
});
