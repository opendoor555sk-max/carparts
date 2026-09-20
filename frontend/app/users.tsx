import { useCallback, useState } from "react";
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { useLanguage } from "@/src/context/LanguageContext";
import { Button, Card, Field, Header, Loading } from "@/src/components/ui";
import { colors, font, radius, shadow, spacing } from "@/src/theme";

export default function Users() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { show } = useToast();
  const { t } = useLanguage();
  const [users, setUsers] = useState<any[]>([]);
  const [allPerms, setAllPerms] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState(false);

  // create form
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [perms, setPerms] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState<any>(null);
  const [removing, setRemoving] = useState(false);

  // password reveal + edit
  const [revealed, setRevealed] = useState<Record<string, string>>({});
  const [editUser, setEditUser] = useState<any>(null);
  const [eName, setEName] = useState("");
  const [eUsername, setEUsername] = useState("");
  const [ePassword, setEPassword] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);
  const [verifying, setVerifying] = useState<string | null>(null);

  const revealPw = async (u: any) => {
    if (revealed[u.id]) {
      setRevealed((r) => {
        const c = { ...r };
        delete c[u.id];
        return c;
      });
      return;
    }
    try {
      const res = await api.get<{ password: string }>(`/admin/users/${u.id}/password`);
      setRevealed((r) => ({ ...r, [u.id]: res.password }));
    } catch (e: any) {
      show(e?.detail?.message || e?.message || t("users.passwordNotFound"), "error");
    }
  };

  const openEdit = (u: any) => {
    setEditUser(u);
    setEName(u.name);
    setEUsername(u.username);
    setEPassword("");
  };

  const saveEdit = async () => {
    if (!editUser) return;
    const body: any = {};
    if (eName.trim() && eName.trim() !== editUser.name) body.name = eName.trim();
    if (eUsername.trim() && eUsername.trim() !== editUser.username) body.username = eUsername.trim();
    if (ePassword) {
      if (ePassword.length < 6) return show(t("users.passwordMinLength"), "error");
      body.password = ePassword;
    }
    if (Object.keys(body).length === 0) {
      setEditUser(null);
      return;
    }
    setSavingEdit(true);
    try {
      await api.patch(`/admin/users/${editUser.id}`, body);
      show(t("users.updated"), "success");
      setRevealed((r) => {
        const c = { ...r };
        delete c[editUser.id];
        return c;
      });
      setEditUser(null);
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setSavingEdit(false);
    }
  };

  const load = useCallback(async () => {
    try {
      const [u, p] = await Promise.all([api.get("/admin/users"), api.get("/permissions")]);
      setUsers(u);
      setAllPerms(p.all);
      if (perms.length === 0) setPerms(p.staff_default);
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setLoading(false);
    }
  }, [t]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load]),
  );

  const togglePerm = (perm: string) => {
    setPerms((cur) => (cur.includes(perm) ? cur.filter((x) => x !== perm) : [...cur, perm]));
  };

  const create = async () => {
    if (!name.trim() || !username.trim() || !password) {
      show(t("users.fillAllFields"), "error");
      return;
    }
    setCreating(true);
    try {
      await api.post("/admin/users", { name, username, password, role: "staff", permissions: perms });
      show(t("users.staffCreated"), "success");
      setModal(false);
      setName("");
      setUsername("");
      setPassword("");
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setCreating(false);
    }
  };

  const toggleDisable = async (u: any) => {
    try {
      await api.patch(`/admin/users/${u.id}`, { disabled: !u.disabled });
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    }
  };

  const verifyUser = async (u: any) => {
    setVerifying(u.id);
    try {
      await api.post(`/admin/users/${u.id}/verify`);
      show(t("users.staffVerifiedToast"), "success");
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setVerifying(null);
    }
  };

  const removeUser = async () => {
    if (!confirmRemove) return;
    setRemoving(true);
    try {
      await api.del(`/admin/users/${confirmRemove.id}`);
      show(`${confirmRemove.name} ${t("users.removedSuffix")}`, "success");
      setConfirmRemove(null);
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    } finally {
      setRemoving(false);
    }
  };

  const toggleUserPerm = async (u: any, perm: string) => {
    const next = u.permissions.includes(perm) ? u.permissions.filter((x: string) => x !== perm) : [...u.permissions, perm];
    try {
      await api.patch(`/admin/users/${u.id}`, { permissions: next });
      load();
    } catch (e: any) {
      show(e?.message || t("common.failed"), "error");
    }
  };

  return (
    <View style={styles.flex}>
      <Header
        title={t("users.title")}
        subtitle={t("users.subtitle")}
        onBack={() => router.back()}
        right={
          <Pressable onPress={() => setModal(true)} style={styles.addBtn} testID="add-user">
            <Ionicons name="person-add" size={18} color={colors.onBrand} />
          </Pressable>
        }
      />
      {loading ? (
        <Loading />
      ) : (
        <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md, paddingBottom: insets.bottom + 40 }}>
          {users.map((u) => (
            <Card key={u.id} testID={`user-${u.username}`}>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.name}>{u.name}</Text>
                  <Text style={styles.username}>@{u.username}</Text>
                  <Text style={styles.addedBy}>
                    {t("users.addedBy")}: {u.created_by?.name || t("users.addedByUnknown")}
                  </Text>
                </View>
                <View style={styles.badgeCol}>
                  <View style={[styles.badge, { backgroundColor: u.verified ? colors.successFaint : colors.warningFaint }]}>
                    <Text style={[styles.badgeText, { color: u.verified ? colors.success : colors.warning }]}>
                      {u.verified ? t("users.verified") : t("users.pending")}
                    </Text>
                  </View>
                  <View style={[styles.badge, { backgroundColor: u.disabled ? colors.errorFaint : colors.successFaint }]}>
                    <Text style={[styles.badgeText, { color: u.disabled ? colors.error : colors.success }]}>
                      {u.disabled ? t("users.disabled") : t("users.active")}
                    </Text>
                  </View>
                </View>
              </View>

              {/* Password reveal + edit (Admin) */}
              <View style={styles.pwRow}>
                <View style={styles.pwBox}>
                  <Ionicons name="key-outline" size={15} color={colors.info} />
                  <Text style={styles.pwText} selectable>
                    {revealed[u.id] ? revealed[u.id] : "••••••••"}
                  </Text>
                </View>
                <Pressable style={styles.pwBtn} onPress={() => revealPw(u)} testID={`reveal-${u.username}`}>
                  <Ionicons name={revealed[u.id] ? "eye-off" : "eye"} size={16} color={colors.brand} />
                  <Text style={styles.pwBtnText}>{revealed[u.id] ? t("users.hide") : t("users.view")}</Text>
                </Pressable>
                <Pressable style={styles.pwBtn} onPress={() => openEdit(u)} testID={`edit-${u.username}`}>
                  <Ionicons name="create-outline" size={16} color={colors.brand} />
                  <Text style={styles.pwBtnText}>{t("users.change")}</Text>
                </Pressable>
              </View>

              {u.role !== "admin" ? (
                <>
                  {!u.verified ? (
                    <Button
                      title={t("users.verifyButton")}
                      onPress={() => verifyUser(u)}
                      loading={verifying === u.id}
                      icon="checkmark-circle"
                      testID={`verify-${u.username}`}
                      style={{ marginTop: spacing.md }}
                    />
                  ) : null}
                  <Text style={styles.permLabel}>{t("users.permissionsTapToggle")}</Text>
                  <View style={styles.permGrid}>
                    {allPerms.map((perm) => {
                      const on = u.permissions.includes(perm);
                      return (
                        <Pressable
                          key={perm}
                          onPress={() => toggleUserPerm(u, perm)}
                          style={[styles.permChip, { backgroundColor: on ? colors.brandFaint : colors.surface, borderColor: on ? colors.brand : colors.border }]}
                          testID={`perm-${u.username}-${perm}`}
                        >
                          <Text style={{ color: on ? colors.brand : colors.info, fontSize: font.sm - 1, fontWeight: "700" }}>{perm}</Text>
                        </Pressable>
                      );
                    })}
                  </View>
                  <View style={[styles.rowBetween, { marginTop: spacing.md }]}>
                    <Text style={styles.label}>{t("users.disabled")}</Text>
                    <Switch
                      value={!!u.disabled}
                      onValueChange={() => toggleDisable(u)}
                      trackColor={{ true: colors.error, false: colors.surface3 }}
                      thumbColor={colors.onSurface}
                      testID={`disable-${u.username}`}
                    />
                  </View>
                  <Pressable style={styles.removeBtn} onPress={() => setConfirmRemove(u)} testID={`remove-${u.username}`}>
                    <Ionicons name="trash" size={16} color={colors.error} />
                    <Text style={styles.removeText}>{t("users.removeUser")}</Text>
                  </Pressable>
                </>
              ) : (
                <Text style={styles.adminNote}>{t("users.mainAdminNote")}</Text>
              )}
            </Card>
          ))}
        </ScrollView>
      )}

      <Modal visible={modal} transparent animationType="slide" onRequestClose={() => setModal(false)}>
        <View style={styles.modalWrap}>
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View style={styles.modal}>
              <View style={styles.modalHead}>
                <Text style={styles.modalTitle}>{t("users.newStaff")}</Text>
                <Pressable onPress={() => setModal(false)} testID="close-user-modal">
                  <Ionicons name="close" size={24} color={colors.onSurface} />
                </Pressable>
              </View>
              <ScrollView keyboardShouldPersistTaps="handled">
                <Field label={t("common.name")} value={name} onChangeText={setName} testID="new-user-name" />
                <Field label={t("users.username")} value={username} onChangeText={setUsername} autoCapitalize="none" testID="new-user-username" />
                <Field label={t("users.password")} value={password} onChangeText={setPassword} secureTextEntry testID="new-user-password" />
                <Text style={styles.permLabel}>{t("users.permissions")}</Text>
                <View style={styles.permGrid}>
                  {allPerms.map((perm) => {
                    const on = perms.includes(perm);
                    return (
                      <Pressable
                        key={perm}
                        onPress={() => togglePerm(perm)}
                        style={[styles.permChip, { backgroundColor: on ? colors.brandFaint : colors.surface, borderColor: on ? colors.brand : colors.border }]}
                        testID={`newperm-${perm}`}
                      >
                        <Text style={{ color: on ? colors.brand : colors.info, fontSize: font.sm - 1, fontWeight: "700" }}>{perm}</Text>
                      </Pressable>
                    );
                  })}
                </View>
                <Button title={t("users.createStaff")} onPress={create} loading={creating} icon="checkmark" testID="create-user" style={{ marginTop: spacing.lg }} />
              </ScrollView>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>

      {/* Edit user modal */}
      <Modal visible={!!editUser} transparent animationType="slide" onRequestClose={() => setEditUser(null)}>
        <View style={styles.modalWrap}>
          <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View style={styles.modal}>
              <View style={styles.modalHead}>
                <Text style={styles.modalTitle}>{t("users.changeUsernamePassword")}</Text>
                <Pressable onPress={() => setEditUser(null)} testID="close-edit-modal">
                  <Ionicons name="close" size={24} color={colors.onSurface} />
                </Pressable>
              </View>
              <ScrollView keyboardShouldPersistTaps="handled">
                <Text style={styles.editWho}>{editUser?.name} (@{editUser?.username})</Text>
                <Field label={t("common.name")} value={eName} onChangeText={setEName} testID="edit-name" />
                <Field label={t("users.usernameLoginName")} value={eUsername} onChangeText={setEUsername} autoCapitalize="none" testID="edit-username" />
                <Field
                  label={t("users.newPasswordLabel")}
                  value={ePassword}
                  onChangeText={setEPassword}
                  placeholder={t("users.newPasswordPlaceholder")}
                  autoCapitalize="none"
                  testID="edit-password"
                />
                <Button title={t("common.saveChanges")} onPress={saveEdit} loading={savingEdit} icon="save" testID="save-edit" style={{ marginTop: spacing.lg }} />
              </ScrollView>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>

      {/* Confirm remove */}
      <Modal visible={!!confirmRemove} transparent animationType="fade" onRequestClose={() => setConfirmRemove(null)}>
        <View style={styles.confirmWrap}>
          <View style={styles.confirmBox}>
            <Ionicons name="warning" size={40} color={colors.error} />
            <Text style={styles.confirmTitle}>{t("users.removeThisUser")}</Text>
            <Text style={styles.confirmSub}>
              {confirmRemove?.name} (@{confirmRemove?.username}) {t("users.removeConfirmSuffix")}
            </Text>
            <View style={styles.confirmRow}>
              <Button title={t("ui.cancel")} onPress={() => setConfirmRemove(null)} variant="secondary" style={{ flex: 1 }} testID="cancel-remove" />
              <Button title={t("users.remove")} onPress={removeUser} loading={removing} variant="danger" style={{ flex: 1 }} testID="confirm-remove" />
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  addBtn: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center", ...shadow.sm },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  name: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
  username: { color: colors.info, fontSize: font.sm },
  addedBy: { color: colors.info, fontSize: font.sm - 1, marginTop: 2 },
  badgeCol: { alignItems: "flex-end", gap: 4 },
  badge: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.pill },
  badgeText: { fontSize: font.sm - 1, fontWeight: "800" },
  permLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 1, marginTop: spacing.md, marginBottom: spacing.sm },
  permGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.xs },
  permChip: { paddingHorizontal: spacing.sm, paddingVertical: 6, borderRadius: radius.sm, borderWidth: 1 },
  label: { color: colors.onSurface2, fontSize: font.base, fontWeight: "600" },
  adminNote: { color: colors.brand, fontSize: font.sm, marginTop: spacing.sm, fontWeight: "700" },
  pwRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginTop: spacing.md },
  pwBox: { flex: 1, flexDirection: "row", alignItems: "center", gap: 6, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 8 },
  pwText: { color: colors.onSurface2, fontSize: font.base, fontWeight: "700", letterSpacing: 1 },
  pwBtn: { flexDirection: "row", alignItems: "center", gap: 4, borderWidth: 1, borderColor: colors.brand, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 8 },
  pwBtnText: { color: colors.brand, fontSize: font.sm, fontWeight: "700" },
  editWho: { color: colors.info, fontSize: font.base, fontWeight: "700", marginBottom: spacing.md },
  removeBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: spacing.xs, marginTop: spacing.md, paddingVertical: spacing.sm, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.error },
  removeText: { color: colors.error, fontSize: font.base, fontWeight: "700" },
  confirmWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", alignItems: "center", justifyContent: "center", padding: spacing.xl },
  confirmBox: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radius.lg, padding: spacing.xl, alignItems: "center", gap: spacing.sm, width: "100%" },
  confirmTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800", marginTop: spacing.sm },
  confirmSub: { color: colors.info, fontSize: font.base, textAlign: "center", lineHeight: 20 },
  confirmRow: { flexDirection: "row", gap: spacing.md, marginTop: spacing.md, width: "100%" },
  modalWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", justifyContent: "flex-end" },
  modal: { backgroundColor: colors.surface, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, padding: spacing.lg, maxHeight: "88%", borderWidth: 1, borderColor: colors.border },
  modalHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: spacing.lg },
  modalTitle: { color: colors.onSurface, fontSize: font.xl, fontWeight: "800" },
});
