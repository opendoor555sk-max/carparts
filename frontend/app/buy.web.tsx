import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  FlatList,
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
import { unstable_createElement } from "react-native-web";
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSequence, withTiming } from "react-native-reanimated";
import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BrowserMultiFormatReader } from "@zxing/browser";
import { BarcodeFormat, DecodeHintType } from "@zxing/library";
import * as Haptics from "expo-haptics";
import * as Location from "expo-location";
import * as ImagePicker from "expo-image-picker";
import { useAudioPlayer } from "expo-audio";

import { api, fileUrl, uploadImage } from "@/src/api/client";
import { useToast } from "@/src/context/ToastContext";
import { Button, Field, FilterChip, Header, LimitBar, StatusChip } from "@/src/components/ui";
import { extractPartNumber } from "@/src/utils/barcode";
import { printReceipt, brandingFromUser } from "@/src/utils/print";
import { colors, font, radius, spacing } from "@/src/theme";
import { useAuth } from "@/src/context/AuthContext";

// This screen used to be "Multiple Buy" (fast scan -> draft -> confirm) with a
// separate, richer single-part "Buy" screen for condition/photos/price/print/
// catalog-autofill/admin-override. The two are merged here: scanning still adds
// a lightweight draft line instantly (unchanged, so bulk scanning stays fast),
// and tapping a line expands it to reveal everything the old single-Buy screen
// could do, per part number, before Confirm writes anything to the backend.

const COMPANIES = ["All", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda", "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet"];
const CONDITIONS = ["Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown"];

// Top-left total-quantity counter: 3x the old header-subtitle size (font.sm=12) is its
// new resting size. Each scan zooms it up to ZOOM_PEAK_SCALE then settles at 50% of
// that peak (i.e. smaller than the peak, not back to 1x) until the next scan.
const COUNTER_BASE_SIZE = font.sm * 3;
const ZOOM_PEAK_SCALE = 1.6;
const ZOOM_SETTLE_SCALE = ZOOM_PEAK_SCALE * 0.5;

type Photo = { path: string; display: string };

type DraftLine = {
  pn: string;
  qty: number;
  expanded: boolean;
  // Per-part metadata — same fields the old single-Buy screen collected,
  // now captured per draft line instead of per screen visit.
  condition: string;
  name: string;
  category: string;
  vehicles: string; // comma-separated, split into an array on submit
  variant: string;
  company: string;
  rack: string;
  shelf: string;
  box: string;
  position: string;
  price: string;
  photos: Photo[];
  override: boolean;
  // GET /search?q= result for this part (has .limit/.part/.catalog/.status),
  // fetched once when the line is first created — powers autofill + the
  // limit card shown when the line is expanded.
  info: any | null;
  infoLoading: boolean;
  searching: boolean; // Google Autofill in flight
  uploading: boolean; // a photo upload in flight
};

export default function BuyWeb() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  // `pn` arrives from screens that already know the part number (part detail's
  // "Buy" button, unlinked-stock's "link" action) and expect it pre-added as a
  // draft line, same as the old single-Buy screen pre-filling its form from it.
  const { company: routeCompany = "All", pn: routePn } = useLocalSearchParams<{ company: string; pn: string }>();
  const { show } = useToast();
  const { user, can } = useAuth();
  const isSuperAdmin = user?.role === "super_admin";
  const [manual, setManual] = useState("");
  // Draft/review list — nothing here has been sent to the backend yet.
  // Scanning only adds/increments a line; stock is written on confirmAndAddToStock().
  const [lines, setLines] = useState<DraftLine[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [gps, setGps] = useState("");
  const videoRef = useRef<any>(null);
  const controlsRef = useRef<any>(null);
  const busy = useRef(false);
  const lastScan = useRef<{ code: string; at: number }>({ code: "", at: 0 });
  const total = useMemo(() => lines.reduce((s, c) => s + c.qty, 0), [lines]);

  const updateLine = useCallback(
    (pn: string, patch: Partial<DraftLine> | ((l: DraftLine) => Partial<DraftLine>)) => {
      setLines((prev) =>
        prev.map((l) => (l.pn === pn ? { ...l, ...(typeof patch === "function" ? (patch as any)(l) : patch) } : l)),
      );
    },
    [],
  );

  // Scan-success flash border + counter zoom, both driven from one trigger.
  const flashOpacity = useSharedValue(0);
  const counterScale = useSharedValue(1);
  const flashStyle = useAnimatedStyle(() => ({ opacity: flashOpacity.value }));
  const counterStyle = useAnimatedStyle(() => ({ transform: [{ scale: counterScale.value }] }));

  const triggerScanFeedback = useCallback(() => {
    flashOpacity.value = withSequence(
      withTiming(1, { duration: 70, easing: Easing.out(Easing.quad) }),
      withTiming(0, { duration: 420, easing: Easing.in(Easing.quad) }),
    );
    counterScale.value = withSequence(
      withTiming(ZOOM_PEAK_SCALE, { duration: 130, easing: Easing.out(Easing.quad) }),
      withTiming(ZOOM_SETTLE_SCALE, { duration: 260, easing: Easing.inOut(Easing.quad) }),
    );
    // expo-haptics has no numeric duration/intensity knob (iOS/Android don't expose
    // one through this API) — so "+50% duration/intensity" is approximated with a
    // second, heavier pulse shortly after the first, extending both the felt
    // duration and strength of the combined haptic event.
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy), 60);
  }, [flashOpacity, counterScale]);

  // ---- Real-time (per-scan) limit warning — separate from the hard, ---
  // authoritative block that confirmAndAddToStock() already gets from /buy's
  // 409 LIMIT_REACHED. This is an early heads-up so a scanning operator sees
  // "stop" the instant they cross the line, instead of only finding out after
  // scanning a whole batch and hitting Confirm. Deliberately loud/jarring —
  // distinct in every channel (color, sound, haptic pattern, copy) from the
  // green "added" feedback above — so it can't be mistaken for a normal scan.
  const dangerFlashOpacity = useSharedValue(0);
  const dangerFlashStyle = useAnimatedStyle(() => ({ opacity: dangerFlashOpacity.value }));
  const [dangerBorder, setDangerBorder] = useState(false);
  const [stopMessage, setStopMessage] = useState<string | null>(null);
  const dangerTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  // A short, harsh hi-lo alarm — nothing else in this app plays a sound, so
  // this alone is enough to read as "different from the normal success beep".
  const dangerPlayer = useAudioPlayer(require("../assets/sounds/limit_reached.wav"));

  const triggerDangerFeedback = useCallback(
    (pn: string) => {
      dangerFlashOpacity.value = withSequence(
        withTiming(1, { duration: 60, easing: Easing.out(Easing.quad) }),
        withTiming(0.15, { duration: 180, easing: Easing.inOut(Easing.quad) }),
        withTiming(1, { duration: 60, easing: Easing.out(Easing.quad) }),
        withTiming(0, { duration: 500, easing: Easing.in(Easing.quad) }),
      );
      setDangerBorder(true);
      setStopMessage(`STOP BUYING ${pn} — Limit Reached`);
      if (dangerTimeout.current) clearTimeout(dangerTimeout.current);
      dangerTimeout.current = setTimeout(() => {
        setDangerBorder(false);
        setStopMessage(null);
      }, 2200);
      // Distinct double-buzz (vs. the single success pulse above) plus the alarm sound.
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy), 90);
      setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy), 260);
      try {
        dangerPlayer.seekTo(0);
        dangerPlayer.play();
      } catch {
        // best-effort — a missing/failed sound must never block the actual warning
      }
    },
    [dangerFlashOpacity, dangerPlayer],
  );

  // Live, per-scan check against GET /limits/{part_number} — the same
  // compute_limit() this screen's own limit card (below) reads. `draftQtyBefore`
  // is how many of this part are ALREADY queued in this draft (not yet actually
  // purchased), since those don't show up in the backend's existing_stock yet
  // but do count toward whether one more would go over. A line with its own
  // Admin Override switch on skips the check entirely, same as the old
  // single-Buy screen's "isStop && !override" gate.
  // Fails OPEN on a network error: the real, authoritative block still
  // happens at Confirm time via /buy's 409 LIMIT_REACHED, so a flaky
  // connection here degrades to "no early warning", never "can't scan".
  const checkUnitAgainstLimit = useCallback(
    async (pn: string, draftQtyBefore: number, override: boolean): Promise<boolean> => {
      if (override) return true;
      try {
        const limit = await api.get(`/limits/${encodeURIComponent(pn)}`);
        if (!limit?.limit_enabled || limit.allowed_limit == null) return true;
        const projected = (limit.existing_stock || 0) + draftQtyBefore + 1;
        if (projected > limit.allowed_limit) {
          triggerDangerFeedback(pn);
          return false;
        }
        return true;
      } catch {
        return true;
      }
    },
    [triggerDangerFeedback],
  );

  // Fired once, when a line is first created — mirrors the old single-Buy
  // screen's load(): pulls existing part/catalog info to auto-fill
  // name/category/vehicles/variant/company, and stores the full result (which
  // includes .limit) for the expanded card's limit display.
  const loadLineInfo = useCallback(
    async (pn: string) => {
      try {
        const res = await api.get(`/search?q=${encodeURIComponent(pn)}`);
        const src = res.part || res.catalog;
        updateLine(pn, (l) => ({
          info: res,
          infoLoading: false,
          name: l.name || src?.name || "",
          category: l.category || src?.category || "",
          variant: l.variant || src?.variant || "",
          vehicles: l.vehicles || (src?.compatible_vehicles?.length ? src.compatible_vehicles.join(", ") : ""),
          company: src?.company && src.company !== "All" && (l.company === "All" || !l.company) ? src.company : l.company,
        }));
        if (!res.part && res.catalog) show(`${pn}: auto-filled from Common Catalog`, "info");
      } catch {
        updateLine(pn, { infoLoading: false });
      }
    },
    [updateLine, show],
  );

  useEffect(() => {
    let sub: Location.LocationSubscription | null = null;
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== "granted") return;
        // initial fix
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
        setGps(`${loc.coords.latitude.toFixed(5)}, ${loc.coords.longitude.toFixed(5)}`);
        // keep GPS live so the correct purchase location is always current
        sub = await Location.watchPositionAsync(
          { accuracy: Location.Accuracy.High, distanceInterval: 3, timeInterval: 4000 },
          (l) => setGps(`${l.coords.latitude.toFixed(5)}, ${l.coords.longitude.toFixed(5)}`),
        );
      } catch {}
    })();
    return () => {
      try {
        if (sub) sub.remove();
      } catch {}
    };
  }, []);

  useEffect(() => {
    return () => {
      if (dangerTimeout.current) clearTimeout(dangerTimeout.current);
    };
  }, []);

  // Scanning is now purely local — it only adds/increments a draft line.
  // Nothing touches the backend until confirmAndAddToStock() is pressed.
  // The limit check below is a network round-trip, so it runs BEFORE the
  // add — a scan that would go over the limit never makes it into the draft
  // at all (capped at the allowed remaining quantity), rather than being
  // added and then un-added.
  const addOne = useCallback(
    async (raw: string) => {
      const pn = extractPartNumber(raw);
      if (!pn || busy.current) return;
      busy.current = true;
      const existing = lines.find((l) => l.pn === pn);
      const allowed = await checkUnitAgainstLimit(pn, existing?.qty || 0, existing?.override || false);
      if (!allowed) {
        setTimeout(() => (busy.current = false), 350);
        return;
      }
      triggerScanFeedback();
      if (existing) {
        updateLine(pn, (l) => ({ qty: l.qty + 1 }));
      } else {
        const fresh: DraftLine = {
          pn,
          qty: 1,
          expanded: false,
          condition: "Working",
          name: "",
          category: "",
          vehicles: "",
          variant: "",
          company: (routeCompany as string) || "All",
          rack: "",
          shelf: "",
          box: "",
          position: "",
          price: "",
          photos: [],
          override: false,
          info: null,
          infoLoading: true,
          searching: false,
          uploading: false,
        };
        setLines((prev) => [fresh, ...prev]);
        loadLineInfo(pn);
      }
      setTimeout(() => (busy.current = false), 350);
    },
    [lines, checkUnitAgainstLimit, triggerScanFeedback, routeCompany, loadLineInfo, updateLine],
  );

  // Pre-add the part number this screen was opened with (e.g. part detail's
  // "Buy" button, or unlinked-stock's "link" action) — once only, same as
  // scanning it, so navigating here still works exactly like it used to when
  // this screen only had a single-item form driven by this same `pn` param.
  // Expanded by default in this case (unlike a normal scan): arriving via a
  // specific part number is a deliberate single-item visit, so show its full
  // details right away instead of requiring an extra tap to expand.
  const initialPnHandled = useRef(false);
  useEffect(() => {
    if (routePn && !initialPnHandled.current) {
      initialPnHandled.current = true;
      const decoded = decodeURIComponent(routePn as string);
      (async () => {
        await addOne(decoded);
        const resolvedPn = extractPartNumber(decoded);
        if (resolvedPn) updateLine(resolvedPn, { expanded: true });
      })();
    }
  }, [routePn, addOne, updateLine]);

  const incQty = useCallback(
    async (pn: string) => {
      // The +1 stepper is just as capable of pushing a line over its limit as
      // another scan would be — same check, so it can't be used to bypass it.
      const line = lines.find((l) => l.pn === pn);
      if (!line) return;
      const allowed = await checkUnitAgainstLimit(pn, line.qty, line.override);
      if (!allowed) return;
      updateLine(pn, (l) => ({ qty: l.qty + 1 }));
    },
    [lines, checkUnitAgainstLimit, updateLine],
  );
  const decQty = useCallback((pn: string) => {
    updateLine(pn, (l) => ({ qty: Math.max(1, l.qty - 1) }));
  }, [updateLine]);
  const removeLine = useCallback((pn: string) => {
    setLines((prev) => prev.filter((l) => l.pn !== pn));
  }, []);
  const toggleExpand = useCallback((pn: string) => {
    updateLine(pn, (l) => ({ expanded: !l.expanded }));
  }, [updateLine]);

  const googleAutofillFor = useCallback(
    async (pn: string) => {
      const line = lines.find((l) => l.pn === pn);
      updateLine(pn, { searching: true });
      try {
        const r = await api.post("/search/web", { part_number: pn, company: line?.company || "All" });
        updateLine(pn, (l) => ({
          name: r.name || l.name,
          vehicles: r.models?.length ? r.models.join(", ") : l.vehicles,
          variant: r.variants?.length ? r.variants.join(", ") : l.variant,
        }));
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
        updateLine(pn, { searching: false });
      }
    },
    [lines, updateLine, show, router],
  );

  const doUploadFor = useCallback(
    async (pn: string, uri: string) => {
      updateLine(pn, { uploading: true });
      try {
        const { path } = await uploadImage(uri);
        const display = await fileUrl(path);
        updateLine(pn, (l) => ({ photos: [...l.photos, { path, display }] }));
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } catch {
        show("Photo upload failed", "error");
      } finally {
        updateLine(pn, { uploading: false });
      }
    },
    [updateLine, show],
  );

  const takePhotoFor = useCallback(
    async (pn: string) => {
      const line = lines.find((l) => l.pn === pn);
      if ((line?.photos.length || 0) >= 6) return show("Maximum 6 photos", "info");
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
      if (!res.canceled && res.assets?.[0]?.uri) await doUploadFor(pn, res.assets[0].uri);
    },
    [lines, show, doUploadFor],
  );

  const pickGalleryFor = useCallback(
    async (pn: string) => {
      const line = lines.find((l) => l.pn === pn);
      const current = line?.photos.length || 0;
      if (current >= 6) return show("Maximum 6 photos", "info");
      let perm = await ImagePicker.getMediaLibraryPermissionsAsync();
      if (!perm.granted && perm.canAskAgain) perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!perm.granted) {
        Alert.alert("Gallery needed", "Allow gallery to add photos.", [
          { text: "Cancel", style: "cancel" },
          { text: "Open Settings", onPress: () => Linking.openSettings() },
        ]);
        return;
      }
      const remaining = 6 - current;
      const res = await ImagePicker.launchImageLibraryAsync({
        quality: 0.6,
        allowsMultipleSelection: true,
        selectionLimit: remaining,
      });
      if (!res.canceled) {
        for (const a of res.assets || []) {
          if (a.uri) await doUploadFor(pn, a.uri);
        }
      }
    },
    [lines, show, doUploadFor],
  );

  const removePhotoFor = useCallback(
    (pn: string, path: string) => {
      updateLine(pn, (l) => ({ photos: l.photos.filter((p) => p.path !== path) }));
    },
    [updateLine],
  );

  const printSlipFor = useCallback(
    async (line: DraftLine) => {
      await printReceipt(await brandingFromUser(user), "BUY", {
        part_number: line.pn,
        name: line.name,
        condition: line.condition,
        location: { rack: line.rack, shelf: line.shelf, box: line.box, position: line.position },
        price: line.price || null,
        by: user?.name,
        qty: line.qty,
      });
    },
    [user],
  );

  // The actual write: one /buy call per unit (the endpoint has no quantity field —
  // each call inserts exactly one stock unit), run sequentially per line, carrying
  // that line's condition/photos/price/etc. onto every unit it covers. A line
  // that partially fails (e.g. a purchase limit hit mid-way) keeps only the
  // still-unadded remainder in the draft, so re-confirming later can't double-add
  // units that already made it into stock.
  const confirmAndAddToStock = useCallback(async () => {
    if (!lines.length || confirming) return;
    setConfirming(true);
    const remaining: DraftLine[] = [];
    const issues: string[] = [];
    let added = 0;
    for (const line of lines) {
      let ok = 0;
      let stopReason = "";
      for (let i = 0; i < line.qty; i++) {
        try {
          await api.post("/buy", {
            part_number: line.pn,
            company: line.company,
            name: line.name,
            category: line.category,
            compatible_vehicles: line.vehicles.split(",").map((s) => s.trim()).filter(Boolean),
            variant: line.variant,
            condition: line.condition,
            location: { rack: line.rack, shelf: line.shelf, box: line.box, position: line.position, gps },
            price: line.price ? parseFloat(line.price) : null,
            photos: line.photos.map((p) => p.path),
            override: line.override,
          });
          ok++;
        } catch (e: any) {
          stopReason = e?.detail?.code === "LIMIT_REACHED" ? "limit reached" : (e?.detail?.message || e?.message || "failed");
          break;
        }
      }
      added += ok;
      const left = line.qty - ok;
      if (left > 0) {
        remaining.push({ ...line, qty: left });
        issues.push(`${line.pn}: added ${ok}/${line.qty}${stopReason ? ` — ${stopReason}` : ""}`);
      }
    }
    setLines(remaining);
    setConfirming(false);
    if (issues.length) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      show(`Added ${added} unit(s) — ${issues.length} line(s) still need attention`, added ? "info" : "error");
      // Refresh the shown limit/stock for whatever got requeued, so the card
      // isn't left displaying stale pre-purchase numbers.
      remaining.forEach((l) => loadLineInfo(l.pn));
    } else {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      show(`Added ${added} unit(s) to stock`, "success");
      router.replace("/(tabs)/inventory" as any);
    }
  }, [lines, confirming, gps, show, router, loadLineInfo]);

  useEffect(() => {
    let cancelled = false;
    const hints = new Map<DecodeHintType, any>();
    hints.set(DecodeHintType.TRY_HARDER, true);
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [
      BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
      BarcodeFormat.EAN_13, BarcodeFormat.UPC_A, BarcodeFormat.ITF, BarcodeFormat.PDF_417,
    ]);
    const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 25 });
    (async () => {
      let t = 0;
      while (!videoRef.current && t < 40) { await new Promise((r) => setTimeout(r, 50)); t++; }
      if (cancelled || !videoRef.current) return;
      try {
        controlsRef.current = await reader.decodeFromConstraints(
          { video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } } },
          videoRef.current,
          (res) => {
            if (!res) return;
            const code = res.getText();
            const now = Date.now();
            if (code === lastScan.current.code && now - lastScan.current.at < 900) return;
            lastScan.current = { code, at: now };
            addOne(code);
          },
        );
      } catch {}
    })();
    return () => { cancelled = true; try { controlsRef.current?.stop?.(); } catch {} };
  }, [addOne]);

  const VideoEl = useMemo(
    () => unstable_createElement("video", { ref: videoRef, autoPlay: true, muted: true, playsInline: true, style: { width: "100%", height: "100%", objectFit: "cover", backgroundColor: "#000" } }),
    [],
  );

  return (
    <View style={[styles.flex, dangerBorder && styles.dangerBorder]} testID="batch-danger-border">
      <Header title="Buy" subtitle={isSuperAdmin ? (gps ? "📍 GPS ✓" : "GPS…") : undefined} onBack={() => router.back()} />
      {stopMessage ? (
        <View style={styles.stopBanner} testID="batch-stop-banner">
          <Ionicons name="hand-left" size={20} color={colors.onError} />
          <Text style={styles.stopBannerText} numberOfLines={2}>{stopMessage}</Text>
        </View>
      ) : null}
      <View style={styles.cam}>
        {VideoEl}
        <Animated.View style={[styles.flashOverlay, flashStyle]} pointerEvents="none" testID="batch-scan-flash" />
        <Animated.View style={[styles.dangerFlashOverlay, dangerFlashStyle]} pointerEvents="none" testID="batch-danger-flash" />
        <View style={styles.overlay} pointerEvents="none">
          <View style={[styles.bracket, dangerBorder && styles.bracketDanger]} />
          <Text style={styles.hint}>Scan → adds to draft below (+1 qty). Nothing saved until you confirm.</Text>
        </View>
        <View style={styles.counterWrap} pointerEvents="none" testID="batch-total-counter">
          <Text style={styles.counterLabel}>TOTAL</Text>
          <Animated.Text style={[styles.counterValue, counterStyle]}>{total}</Animated.Text>
        </View>
      </View>

      <View style={styles.inputRow}>
        <View style={{ flex: 1 }}>
          <Field value={manual} onChangeText={setManual} placeholder="Manual part no. +1" autoCapitalize="characters" onSubmitEditing={() => { addOne(manual); setManual(""); }} returnKeyType="done" testID="batch-manual" />
        </View>
        <Pressable style={styles.addBtn} onPress={() => { addOne(manual); setManual(""); }} testID="batch-add"><Ionicons name="add" size={24} color={colors.onBrand} /></Pressable>
      </View>

      {isSuperAdmin ? (
        <View style={styles.gpsStrip}>
          <Ionicons name="location" size={16} color={gps ? colors.success : colors.warning} />
          <Text style={[styles.gpsStripText, { color: gps ? colors.success : colors.warning }]} numberOfLines={1}>
            {gps ? `Live GPS: ${gps}` : "Getting GPS…"}
          </Text>
        </View>
      ) : null}

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <FlatList
          data={lines}
          keyExtractor={(c) => c.pn}
          contentContainerStyle={{ padding: spacing.lg, gap: spacing.sm, paddingBottom: insets.bottom + 90 }}
          keyboardShouldPersistTaps="handled"
          ListEmptyComponent={<Text style={styles.empty}>Nothing scanned yet</Text>}
          renderItem={({ item }) => {
            const limit = item.info?.limit;
            const isStop = limit?.limit_enabled && limit?.remaining !== null && limit?.remaining <= 0;
            const isWarn = limit?.status === "WARNING";
            return (
              <View style={[styles.lineCard, isStop && !item.override && styles.lineCardStop]} testID={`batch-${item.pn}`}>
                <Pressable style={styles.lineHeader} onPress={() => toggleExpand(item.pn)} testID={`batch-toggle-${item.pn}`}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.pn} numberOfLines={2}>{item.pn}</Text>
                    {limit?.limit_enabled ? (
                      <Text style={[styles.lineLimitHint, { color: isStop ? colors.error : colors.info }]}>
                        Stock {limit.existing_stock} / Limit {limit.allowed_limit}
                      </Text>
                    ) : item.infoLoading ? (
                      <Text style={styles.lineLimitHint}>Loading…</Text>
                    ) : null}
                  </View>
                  <View style={styles.qtyControls}>
                    <Pressable style={styles.qtyBtn} onPress={() => decQty(item.pn)} testID={`batch-minus-${item.pn}`}>
                      <Ionicons name="remove" size={18} color={colors.onSurface} />
                    </Pressable>
                    <View style={styles.qtyBadge}><Text style={styles.qtyText}>{item.qty}</Text></View>
                    <Pressable style={styles.qtyBtn} onPress={() => incQty(item.pn)} testID={`batch-plus-${item.pn}`}>
                      <Ionicons name="add" size={18} color={colors.onSurface} />
                    </Pressable>
                    <Pressable style={styles.delBtn} onPress={() => removeLine(item.pn)} testID={`batch-remove-${item.pn}`}>
                      <Ionicons name="trash" size={18} color={colors.error} />
                    </Pressable>
                    <Ionicons name={item.expanded ? "chevron-up" : "chevron-down"} size={20} color={colors.info} style={{ marginLeft: spacing.xs }} />
                  </View>
                </Pressable>

                {item.expanded ? (
                  <View style={styles.lineBody}>
                    {/* Limit meter */}
                    <View style={styles.rowBetween}>
                      <Text style={styles.cardTitle}>PURCHASE LIMIT</Text>
                      {item.info?.status ? <StatusChip status={item.info.status} /> : null}
                    </View>
                    <LimitBar existing={limit?.existing_stock ?? 0} allowed={limit?.allowed_limit ?? null} />
                    {isStop && !item.override ? (
                      <View style={styles.doNotBuy} testID={`batch-do-not-buy-${item.pn}`}>
                        <Ionicons name="hand-left" size={18} color={colors.onError} />
                        <Text style={styles.doNotBuyText}>DO NOT BUY — limit reached</Text>
                      </View>
                    ) : isWarn ? (
                      <View style={styles.warnBanner}>
                        <Ionicons name="warning" size={16} color={colors.onWarning} />
                        <Text style={styles.warnText}>WARNING — near limit</Text>
                      </View>
                    ) : null}

                    {/* Condition */}
                    <Text style={styles.sectionTitle}>CONDITION</Text>
                    <View style={styles.condGrid}>
                      {CONDITIONS.map((c) => (
                        <Pressable
                          key={c}
                          onPress={() => updateLine(item.pn, { condition: c })}
                          style={[
                            styles.condChip,
                            { backgroundColor: item.condition === c ? colors.brand : colors.surface, borderColor: item.condition === c ? colors.brand : colors.border },
                          ]}
                          testID={`batch-cond-${item.pn}-${c}`}
                        >
                          <Text style={{ color: item.condition === c ? colors.onBrand : colors.onSurface2, fontWeight: "700", fontSize: font.sm }}>{c}</Text>
                        </Pressable>
                      ))}
                    </View>

                    {/* Part & compatibility */}
                    <Text style={styles.sectionTitle}>PART & COMPATIBILITY</Text>
                    <Button
                      title="🔍 Google Autofill (your key)"
                      onPress={() => googleAutofillFor(item.pn)}
                      loading={item.searching}
                      variant="secondary"
                      icon="search"
                      testID={`batch-autofill-${item.pn}`}
                      style={{ marginBottom: spacing.md }}
                    />
                    <Field label="Name" value={item.name} onChangeText={(v) => updateLine(item.pn, { name: v })} placeholder="Part name" testID={`batch-name-${item.pn}`} />
                    <Text style={styles.pickLabel}>COMPANY</Text>
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.pickRow}>
                      {COMPANIES.map((c) => (
                        <FilterChip key={c} label={c} active={item.company === c} onPress={() => updateLine(item.pn, { company: c })} testID={`batch-co-${item.pn}-${c}`} />
                      ))}
                    </ScrollView>
                    <Field label="Category" value={item.category} onChangeText={(v) => updateLine(item.pn, { category: v })} placeholder="Category" testID={`batch-category-${item.pn}`} />
                    <Field
                      label="Compatible Vehicles (comma separated)"
                      value={item.vehicles}
                      onChangeText={(v) => updateLine(item.pn, { vehicles: v })}
                      placeholder="Hyundai Creta, Kia Seltos"
                      testID={`batch-vehicles-${item.pn}`}
                    />
                    <Field label="Variant" value={item.variant} onChangeText={(v) => updateLine(item.pn, { variant: v })} placeholder="e.g. HTC Diesel" testID={`batch-variant-${item.pn}`} />

                    {/* Location */}
                    <Text style={styles.sectionTitle}>LOCATION (Rack → Shelf → Box → Position)</Text>
                    <View style={styles.locGrid}>
                      <View style={styles.locItem}><Field label="Rack" value={item.rack} onChangeText={(v) => updateLine(item.pn, { rack: v })} placeholder="R1" testID={`batch-rack-${item.pn}`} /></View>
                      <View style={styles.locItem}><Field label="Shelf" value={item.shelf} onChangeText={(v) => updateLine(item.pn, { shelf: v })} placeholder="S2" testID={`batch-shelf-${item.pn}`} /></View>
                      <View style={styles.locItem}><Field label="Box" value={item.box} onChangeText={(v) => updateLine(item.pn, { box: v })} placeholder="B3" testID={`batch-box-${item.pn}`} /></View>
                      <View style={styles.locItem}><Field label="Position" value={item.position} onChangeText={(v) => updateLine(item.pn, { position: v })} placeholder="P4" testID={`batch-position-${item.pn}`} /></View>
                    </View>

                    {/* Photos */}
                    <View style={styles.rowBetween}>
                      <Text style={styles.sectionTitle}>PART PHOTOS (6-side)</Text>
                      <Text style={styles.photoCount}>{item.photos.length}/6</Text>
                    </View>
                    <View style={styles.photoGrid}>
                      {item.photos.map((p) => (
                        <View key={p.path} style={styles.thumbWrap}>
                          <Image source={{ uri: p.display }} style={styles.thumb} contentFit="cover" />
                          <Pressable style={styles.thumbDel} onPress={() => removePhotoFor(item.pn, p.path)} testID={`batch-del-photo-${item.pn}-${p.path}`}>
                            <Ionicons name="close" size={14} color={colors.onError} />
                          </Pressable>
                        </View>
                      ))}
                      {item.photos.length < 6 ? (
                        <Pressable style={styles.addPhoto} onPress={() => takePhotoFor(item.pn)} disabled={item.uploading} testID={`batch-take-photo-${item.pn}`}>
                          <Ionicons name={item.uploading ? "hourglass" : "camera"} size={20} color={colors.brand} />
                          <Text style={styles.addPhotoText}>Camera</Text>
                        </Pressable>
                      ) : null}
                      {item.photos.length < 6 ? (
                        <Pressable style={styles.addPhoto} onPress={() => pickGalleryFor(item.pn)} disabled={item.uploading} testID={`batch-pick-gallery-${item.pn}`}>
                          <Ionicons name="images" size={20} color={colors.brand} />
                          <Text style={styles.addPhotoText}>Gallery</Text>
                        </Pressable>
                      ) : null}
                    </View>

                    {/* Admin-only price */}
                    {can("view_price") ? (
                      <>
                        <Text style={styles.sectionTitle}>PURCHASE PRICE (this line)</Text>
                        <Field value={item.price} onChangeText={(v) => updateLine(item.pn, { price: v })} placeholder="₹ 0" keyboardType="numeric" testID={`batch-price-${item.pn}`} />
                      </>
                    ) : null}

                    {/* Admin override */}
                    {can("manage_limits") ? (
                      <View style={styles.rowBetween}>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.overrideTitle}>Admin Override</Text>
                          <Text style={styles.dimText}>Buy this line ignoring the limit</Text>
                        </View>
                        <Switch
                          value={item.override}
                          onValueChange={(v) => updateLine(item.pn, { override: v })}
                          trackColor={{ true: colors.brand, false: colors.surface3 }}
                          thumbColor={colors.onSurface}
                          testID={`batch-override-${item.pn}`}
                        />
                      </View>
                    ) : null}

                    <Button
                      title="Print Slip"
                      onPress={() => printSlipFor(item)}
                      variant="secondary"
                      icon="print"
                      testID={`batch-print-${item.pn}`}
                      style={{ marginTop: spacing.sm }}
                    />
                  </View>
                ) : null}
              </View>
            );
          }}
        />
      </KeyboardAvoidingView>
      <View style={[styles.bar, { paddingBottom: insets.bottom + spacing.md }]}>
        <Button
          title={confirming ? "Adding to stock…" : `Confirm & Add to Stock (${total})`}
          onPress={confirmAndAddToStock}
          loading={confirming}
          disabled={confirming || lines.length === 0}
          icon="checkmark-circle"
          testID="batch-confirm"
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.surface },
  // Full-screen red border for the duration of a limit-reached warning —
  // visible even if the operator's attention is on the draft list below,
  // not just the camera.
  dangerBorder: { borderWidth: 4, borderColor: colors.error },
  cam: { height: 280, backgroundColor: "#000", overflow: "hidden" },
  dimText: { color: colors.info, fontSize: font.sm },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: "center", justifyContent: "center", gap: spacing.md },
  // Box +30% (220x120 -> 286x156); border 5x thinner than the prior 30 -> 6.
  bracket: { width: 286, height: 156, borderWidth: 6, borderColor: colors.success, borderRadius: radius.md },
  bracketDanger: { borderColor: colors.error },
  hint: { color: "#fff", fontWeight: "700", fontSize: font.base },
  flashOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.success },
  // Same mechanism/position as flashOverlay above, red instead of green —
  // the "similar to the existing success flash, but red" cue over the camera.
  dangerFlashOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.error },
  stopBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    backgroundColor: colors.error,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  stopBannerText: { color: colors.onError, fontWeight: "900", fontSize: font.lg, letterSpacing: 0.5, flex: 1 },
  counterWrap: {
    position: "absolute",
    top: spacing.sm,
    left: spacing.sm,
    backgroundColor: "rgba(0,0,0,0.55)",
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    alignItems: "flex-start",
  },
  counterLabel: { color: "#fff", fontSize: font.sm, fontWeight: "700", letterSpacing: 1, opacity: 0.85 },
  counterValue: { color: colors.success, fontSize: COUNTER_BASE_SIZE, lineHeight: COUNTER_BASE_SIZE * 1.05, fontWeight: "900" },
  inputRow: { flexDirection: "row", gap: spacing.sm, padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.divider },
  addBtn: { width: 52, height: 52, borderRadius: radius.md, backgroundColor: colors.brand, alignItems: "center", justifyContent: "center" },
  empty: { color: colors.info, textAlign: "center", marginTop: spacing.xl },
  gpsStrip: { flexDirection: "row", alignItems: "center", gap: spacing.xs, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm, backgroundColor: colors.surface2, borderBottomWidth: 1, borderBottomColor: colors.divider },
  gpsStripText: { fontSize: font.base, fontWeight: "700", flex: 1 },
  lineCard: { backgroundColor: colors.surface2, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, overflow: "hidden" },
  lineCardStop: { borderColor: colors.error },
  lineHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md, paddingHorizontal: spacing.lg, paddingVertical: spacing.lg },
  lineLimitHint: { fontSize: font.sm, fontWeight: "700", marginTop: 2 },
  lineBody: { padding: spacing.lg, paddingTop: 0, borderTopWidth: 1, borderTopColor: colors.divider, gap: spacing.sm },
  pn: { color: colors.onSurface, fontSize: 32, lineHeight: 38, fontWeight: "900", letterSpacing: 1, flex: 1 },
  qtyControls: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  qtyBtn: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center" },
  delBtn: { width: 36, height: 36, borderRadius: radius.sm, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.error, alignItems: "center", justifyContent: "center", marginLeft: spacing.xs },
  qtyBadge: { backgroundColor: colors.success, borderRadius: radius.md, paddingHorizontal: spacing.md, paddingVertical: spacing.sm, minWidth: 48, alignItems: "center" },
  qtyText: { color: colors.onSuccess, fontWeight: "900", fontSize: 22 },
  bar: { position: "absolute", bottom: 0, left: 0, right: 0, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border, padding: spacing.md },
  cardTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1 },
  sectionTitle: { color: colors.info, fontSize: font.sm, fontWeight: "800", letterSpacing: 1, marginTop: spacing.sm },
  rowBetween: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  doNotBuy: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.error, borderRadius: radius.md, padding: spacing.md },
  doNotBuyText: { color: colors.onError, fontWeight: "800", fontSize: font.base },
  warnBanner: { flexDirection: "row", alignItems: "center", gap: spacing.sm, backgroundColor: colors.warning, borderRadius: radius.md, padding: spacing.md },
  warnText: { color: colors.onWarning, fontWeight: "800", fontSize: font.base },
  condGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  condChip: { paddingHorizontal: spacing.md, paddingVertical: spacing.sm, borderRadius: radius.pill, borderWidth: 1 },
  pickLabel: { color: colors.info, fontSize: font.sm - 1, fontWeight: "800", letterSpacing: 0.5, marginTop: spacing.xs, marginBottom: spacing.xs },
  pickRow: { gap: spacing.sm, paddingVertical: spacing.xs, paddingRight: spacing.md },
  locGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  locItem: { width: "48%" },
  photoCount: { color: colors.info, fontSize: font.sm, fontWeight: "800" },
  photoGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  thumbWrap: { width: 64, height: 64, borderRadius: radius.sm, overflow: "hidden", position: "relative" },
  thumb: { width: "100%", height: "100%", backgroundColor: colors.surface3 },
  thumbDel: { position: "absolute", top: 2, right: 2, width: 20, height: 20, borderRadius: 10, backgroundColor: colors.error, alignItems: "center", justifyContent: "center" },
  addPhoto: { width: 64, height: 64, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.brandFaint, backgroundColor: colors.surface, alignItems: "center", justifyContent: "center", gap: 2 },
  addPhotoText: { color: colors.brand, fontSize: font.sm - 2, fontWeight: "700" },
  overrideTitle: { color: colors.onSurface, fontSize: font.lg, fontWeight: "800" },
});
