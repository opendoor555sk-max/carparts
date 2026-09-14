import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";

import { api } from "@/src/api/client";
import { useAuth } from "@/src/context/AuthContext";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, ConfirmModal, EmptyState, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, spacing } from "@/src/theme";

export default function VendorDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const vendorId = id as string;
  const router = useRouter();
  const { user } = useAuth();
  const { show } = useToast();
  const { t } = useLanguage();
  const isAdmin = user?.role === "admin" || user?.role === "super_admin";

  const [vendor, setVendor] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    try {
      const v = await api.get(`/vendors/${encodeURIComponent(vendorId)}`);
      setVendor(v);
      setName(v.name);
      setPhone(v.phone);
      setAddress(v.address || "");
      setNotes(v.notes || "");
    } catch (e: any) {
      show(e?.message || t("vendors.loadFailed"), "error");
      setVendor(null);
    } finally {
      setLoading(false);
    }
  }, [vendorId, show, t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const saveEdit = async () => {
    if (!name.trim() || !phone.trim()) {
      show(t("customers.errRequired"), "error");
      return;
    }
    setSaving(true);
    try {
      await api.patch(`/vendors/${encodeURIComponent(vendorId)}`, {
        name: name.trim(), phone: phone.trim(), address: address.trim(), notes: notes.trim(),
      });
      show(t("vendors.updated"), "success");
      setEditing(false);
      load();
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("common.updateFailed"), "error");
    } finally {
      setSaving(false);
    }
  };

  const doDelete = async () => {
    setDeleting(true);
    try {
      await api.del(`/vendors/${encodeURIComponent(vendorId)}`);
      show(t("vendors.deleted"), "success");
      router.replace("/vendors" as any);
    } catch (e: any) {
      show(e?.detail?.message || e?.detail || e?.message || t("vendors.deleteFailed"), "error");
    } finally {
      setDeleting(false);
      setConfirmDelete(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.flex}>
        <Header title={t("vendors.detailTitle")} onBack={() => router.back()} />
        <Loading />
      </View>
    );
  }

  if (!vendor) {
    return (
      <View style={styles.flex}>
        <Header title={t("vendors.detailTitle")} onBack={() => router.back()} />
        <EmptyState icon="briefcase-outline" title={t("vendors.notFound")} />
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <Header title={vendor.name} subtitle={vendor.phone} onBack={() => router.back()} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }}>
          <Card>
            <View style={styles.rowBetween}>
              <Text style={styles.cardTitle}>{t("vendors.details").toUpperCase()}</Text>
              <Button
                title={editing ? t("ui.cancel") : t("common.edit")}
                onPress={() => setEditing((e) => !e)}
                variant="ghost"
                icon={editing ? "close" : "pencil"}
                testID="vendor-edit-toggle"
              />
            </View>
            {editing ? (
              <>
                <Field label={t("common.name")} value={name} onChangeText={setName} placeholder={t("vendors.namePlaceholder")} testID="edit-vendor-name" />
                <Field label={t("common.phone")} value={phone} onChangeText={setPhone} placeholder={t("common.phoneNumber")} keyboardType="phone-pad" testID="edit-vendor-phone" />
                <Field label={t("common.address")} value={address} onChangeText={setAddress} placeholder={t("common.address")} multiline testID="edit-vendor-address" />
                <Field label={t("vendors.notes")} value={notes} onChangeText={setNotes} placeholder="e.g. Electrical parts, OEM Maruti" multiline testID="edit-vendor-notes" />
                <Button title={t("common.saveChanges")} onPress={saveEdit} loading={saving} icon="checkmark-circle" testID="vendor-edit-save" />
              </>
            ) : (
              <>
                {vendor.address ? (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>{t("common.address")}</Text>
                    <Text style={styles.detailValue}>{vendor.address}</Text>
                  </View>
                ) : null}
                {vendor.notes ? (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>{t("vendors.notes")}</Text>
                    <Text style={styles.detailValue}>{vendor.notes}</Text>
                  </View>
                ) : null}
                {!vendor.address && !vendor.notes ? <Text style={styles.emptyDetail}>{t("vendors.noAddressNotes")}</Text> : null}
              </>
            )}
          </Card>

          {isAdmin ? (
            <Button
              title={t("vendors.delete")}
              onPress={() => setConfirmDelete(true)}
              variant="danger"
              icon="trash"
              testID="vendor-delete"
            />
          ) : null}
        </ScrollView>
      </KeyboardAvoidingView>

      <ConfirmModal
        visible={confirmDelete}
        title={t("vendors.deleteConfirmTitle")}
        message={`${vendor.name} ${t("vendors.deleteConfirmMsg")}`}
        confirmText={t("common.delete")}
        danger
        loading={deleting}
        onConfirm={doDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginBottom: spacing.sm },
  detailRow: { marginBottom: spacing.md },
  detailLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "700", letterSpacing: 0.5, marginBottom: 2 },
  detailValue: { color: colors.onSurface2, fontSize: font.base },
  emptyDetail: { color: colors.info, textAlign: "center", paddingVertical: spacing.md },
});
