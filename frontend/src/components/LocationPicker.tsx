import { Pressable, StyleSheet, Text, View } from "react-native";

import { Field } from "@/src/components/ui";
import { colors, font, radius, spacing } from "@/src/theme";

// Hierarchical shelf/rack address, mirroring backend's AssignedLocationIn.
// Renders as: "Store [name] · [Wall] · [Rack name OR Open Floor + Carton
// number] · [Shelf]".
export type AssignedLocation = {
  store_name: string | null;
  wall: string | null; // Front / Back / Left / Right
  rack_name: string | null;
  is_open_floor: boolean;
  carton_number: string | null;
  shelf_level: string | null; // Top / Middle / Bottom — Rack only
};

export const EMPTY_LOCATION: AssignedLocation = {
  store_name: null,
  wall: null,
  rack_name: null,
  is_open_floor: false,
  carton_number: null,
  shelf_level: null,
};

const WALLS = ["Front", "Back", "Left", "Right"] as const;
const SHELVES = ["Top", "Middle", "Bottom"] as const;

export function isLocationEmpty(loc: AssignedLocation): boolean {
  return !loc.store_name && !loc.wall && !loc.rack_name && !loc.carton_number && !loc.shelf_level;
}

export function formatAssignedLocation(loc?: Partial<AssignedLocation> | null): string {
  if (!loc) return "";
  const parts: string[] = [];
  if (loc.store_name) parts.push(`Store ${loc.store_name}`);
  if (loc.wall) parts.push(loc.wall);
  if (loc.is_open_floor) {
    parts.push(loc.carton_number ? `Open Floor · Carton ${loc.carton_number}` : "Open Floor");
  } else if (loc.rack_name) {
    parts.push(`Rack ${loc.rack_name}`);
  }
  if (!loc.is_open_floor && loc.shelf_level) parts.push(loc.shelf_level);
  return parts.join(" · ");
}

// Turns an AssignedLocation into the query-param shape /inventory/location-check
// expects for its `current_*` fields.
export function locationToQueryParams(
  loc: AssignedLocation,
  prefix = "current_",
): Record<string, string> {
  const out: Record<string, string> = {};
  if (loc.store_name) out[`${prefix}store_name`] = loc.store_name;
  if (loc.wall) out[`${prefix}wall`] = loc.wall;
  out[`${prefix}is_open_floor`] = loc.is_open_floor ? "true" : "false";
  if (loc.is_open_floor) {
    if (loc.carton_number) out[`${prefix}carton_number`] = loc.carton_number;
  } else {
    if (loc.rack_name) out[`${prefix}rack_name`] = loc.rack_name;
    if (loc.shelf_level) out[`${prefix}shelf_level`] = loc.shelf_level;
  }
  return out;
}

// Step picker: (a) store name [skippable] -> (b) wall -> (c) rack vs open
// floor -> (d) shelf level [rack only].
export function LocationPicker({
  value,
  onChange,
  showStoreName = true,
  label,
  testIDPrefix = "loc",
}: {
  value: AssignedLocation;
  onChange: (next: AssignedLocation) => void;
  showStoreName?: boolean;
  label?: string;
  testIDPrefix?: string;
}) {
  const set = (patch: Partial<AssignedLocation>) => onChange({ ...value, ...patch });

  return (
    <View style={styles.wrap} testID={`${testIDPrefix}-picker`}>
      {label ? <Text style={styles.sectionLabel}>{label}</Text> : null}

      {showStoreName ? (
        <Field
          value={value.store_name || ""}
          onChangeText={(t) => set({ store_name: t })}
          placeholder="Store name"
          autoCapitalize="words"
          testID={`${testIDPrefix}-store-name`}
        />
      ) : null}

      <Text style={styles.stepLabel}>Wall</Text>
      <View style={styles.row}>
        {WALLS.map((w) => (
          <Pressable
            key={w}
            style={[styles.optBtn, value.wall === w && styles.optBtnActive]}
            onPress={() => set({ wall: value.wall === w ? null : w })}
            testID={`${testIDPrefix}-wall-${w}`}
          >
            <Text style={[styles.optText, value.wall === w && styles.optTextActive]}>{w}</Text>
          </Pressable>
        ))}
      </View>

      <Text style={styles.stepLabel}>Placement</Text>
      <View style={styles.row}>
        <Pressable
          style={[styles.optBtn, !value.is_open_floor && styles.optBtnActive]}
          onPress={() => set({ is_open_floor: false, carton_number: null })}
          testID={`${testIDPrefix}-mode-rack`}
        >
          <Text style={[styles.optText, !value.is_open_floor && styles.optTextActive]}>Rack</Text>
        </Pressable>
        <Pressable
          style={[styles.optBtn, value.is_open_floor && styles.optBtnActive]}
          onPress={() => set({ is_open_floor: true, rack_name: null, shelf_level: null })}
          testID={`${testIDPrefix}-mode-openfloor`}
        >
          <Text style={[styles.optText, value.is_open_floor && styles.optTextActive]}>Open Floor</Text>
        </Pressable>
      </View>

      {value.is_open_floor ? (
        <Field
          value={value.carton_number || ""}
          onChangeText={(t) => set({ carton_number: t.replace(/[^0-9]/g, "") })}
          placeholder="Carton number"
          keyboardType="number-pad"
          testID={`${testIDPrefix}-carton-number`}
        />
      ) : (
        <Field
          value={value.rack_name || ""}
          onChangeText={(t) => set({ rack_name: t })}
          placeholder="Rack name — e.g. A-3"
          autoCapitalize="characters"
          testID={`${testIDPrefix}-rack-name`}
        />
      )}

      {!value.is_open_floor ? (
        <>
          <Text style={styles.stepLabel}>Shelf</Text>
          <View style={styles.row}>
            {SHELVES.map((s) => (
              <Pressable
                key={s}
                style={[styles.optBtn, value.shelf_level === s && styles.optBtnActive]}
                onPress={() => set({ shelf_level: value.shelf_level === s ? null : s })}
                testID={`${testIDPrefix}-shelf-${s}`}
              >
                <Text style={[styles.optText, value.shelf_level === s && styles.optTextActive]}>{s}</Text>
              </Pressable>
            ))}
          </View>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  sectionLabel: { color: colors.onSurface, fontWeight: "800", fontSize: font.base, marginBottom: spacing.xs },
  stepLabel: { color: colors.info, fontWeight: "700", fontSize: font.sm, marginTop: spacing.xs },
  row: { flexDirection: "row", gap: spacing.sm, flexWrap: "wrap", marginBottom: spacing.xs },
  optBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface2,
  },
  optBtnActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  optText: { color: colors.onSurface2, fontWeight: "700", fontSize: font.sm },
  optTextActive: { color: colors.onBrand },
});
