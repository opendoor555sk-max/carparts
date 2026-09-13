import { useCallback, useRef, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { Button, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

type Vendor = { id: string; name: string; phone: string; address?: string; notes?: string };

// Vendor Directory (Supplier Records) — a plain directory, structurally the
// same list+search shape as customers.tsx, just without the ledger/balance
// (vendors are who parts are bought FROM; no credit is tracked here).
export default function Vendors() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(true);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async (query: string) => {
    try {
      const params = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : "";
      setVendors(await api.get<Vendor[]>(`/vendors${params}`));
    } catch {
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load(q);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [load]),
  );

  const onChangeQuery = (text: string) => {
    setQ(text);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => load(text), 300);
  };

  return (
    <View style={styles.flex}>
      <Header title="Vendor Directory" subtitle="Supplier records" onBack={() => router.back()} />

      <View style={styles.searchRow}>
        <View style={{ flex: 1 }}>
          <Field value={q} onChangeText={onChangeQuery} placeholder="Search by name or phone" testID="vendor-search" />
        </View>
        <Pressable style={styles.iconBtn} onPress={() => router.push("/vendor-new" as any)} testID="vendor-add-btn">
          <Ionicons name="add" size={24} color={colors.onBrand} />
        </Pressable>
      </View>

      {loading ? (
        <Loading />
      ) : vendors.length === 0 ? (
        <EmptyState
          icon="briefcase-outline"
          title="No vendors"
          subtitle={q ? `Nothing found for "${q}"` : "Add a vendor to start your supplier directory"}
          action={<Button title="Add Vendor" onPress={() => router.push("/vendor-new" as any)} icon="add-circle" testID="vendor-add-empty" />}
        />
      ) : (
        <FlatList
          data={vendors}
          keyExtractor={(v) => v.id}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm }}
          renderItem={({ item }) => (
            <Pressable style={styles.row} onPress={() => router.push(`/vendor/${item.id}` as any)} testID={`vendor-${item.id}`}>
              <View style={styles.avatar}>
                <Ionicons name="briefcase" size={20} color={colors.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.name}>{item.name}</Text>
                <Text style={styles.phone}>{item.phone}</Text>
                {item.notes ? <Text style={styles.notes} numberOfLines={1}>{item.notes}</Text> : null}
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.info} />
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  searchRow: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.divider },
  iconBtn: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, padding: spacing.lg },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.brandFaint, alignItems: "center", justifyContent: "center" },
  name: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  phone: { color: colors.info, fontSize: font.sm, marginTop: 2 },
  notes: { color: colors.onSurface2, fontSize: font.sm, marginTop: 2 },
});
