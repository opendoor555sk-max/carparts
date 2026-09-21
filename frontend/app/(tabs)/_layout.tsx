import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { Platform } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors, font, spacing } from "@/src/theme";
import { useLowStockCount } from "@/src/hooks/use-low-stock-count";
import { useLanguage } from "@/src/context/LanguageContext";

// ~10mm of extra clearance so the tab bar's icons/labels sit further up from
// the bottom edge, composed from the existing spacing scale (xxl + xs =
// 32 + 4 = 36px) rather than a new magic number — lands in the ~35-40px
// range 10mm works out to at typical phone pixel densities. Added to both
// height and paddingBottom so the bar grows a taller "floor" underneath the
// icons instead of shrinking their existing tap-target area.
const TAB_BAR_LIFT = spacing.xxl + spacing.xs; // 36

export default function TabsLayout() {
  const lowStockCount = useLowStockCount();
  const { t } = useLanguage();
  // Real device safe-area inset (Android system nav bar height when
  // edgeToEdgeEnabled is on, or the iOS home-indicator strip) instead of a
  // guessed platform constant. Guessing under-shoots on phones with a taller
  // gesture/nav bar, which is exactly what was making our tab bar buttons
  // (Home/Inventory/Report/Admin) sit underneath — and get covered by — the
  // phone's own on-screen Home/Back buttons.
  const insets = useSafeAreaInsets();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.info,
        tabBarStyle: {
          backgroundColor: colors.surface2,
          borderTopColor: colors.border,
          borderTopWidth: 1,
          height: (Platform.OS === "ios" ? 60 : 64) + TAB_BAR_LIFT + insets.bottom,
          paddingBottom: 8 + TAB_BAR_LIFT + insets.bottom,
          paddingTop: 8,
        },
        tabBarLabelStyle: { fontSize: font.sm - 1, fontWeight: "700" },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: t("tabs.home"),
          tabBarIcon: ({ color, size }) => <Ionicons name="grid" size={size} color={color} />,
        }}
      />
      {/* Inventory kept as a real, navigable route (still linked from Home,
          the low-stock banner, and the Reports tab overview screen) but no
          longer its own bottom-tab button — same href: null pattern used
          for Requirements/Needs below, now redundant here too since
          Inventory is already one tap away from the Reports tab. */}
      <Tabs.Screen
        name="inventory"
        options={{
          title: t("tabs.inventory"),
          tabBarIcon: ({ color, size }) => <Ionicons name="cube" size={size} color={color} />,
          tabBarBadge: lowStockCount > 0 ? lowStockCount : undefined,
          tabBarBadgeStyle: { backgroundColor: colors.error },
          href: null,
        }}
      />
      <Tabs.Screen
        name="reports"
        options={{
          title: t("tabs.reports"),
          tabBarIcon: ({ color, size }) => <Ionicons name="bar-chart" size={size} color={color} />,
        }}
      />
      {/* Requirements/Needs kept as a real, navigable route (still linked
          from Home, admin.tsx, requirement-new.tsx, and the new Reports
          tab above) but no longer its own bottom-tab button — href: null
          hides it from the tab bar without removing the route, keeping
          the bar at 5 tabs instead of 6. */}
      <Tabs.Screen
        name="requirements"
        options={{
          title: t("tabs.needs"),
          tabBarIcon: ({ color, size }) => <Ionicons name="list-circle" size={size} color={color} />,
          href: null,
        }}
      />
      <Tabs.Screen
        name="categories"
        options={{
          title: t("tabs.catalog"),
          tabBarIcon: ({ color, size }) => <Ionicons name="albums" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="admin"
        options={{
          title: t("tabs.admin"),
          tabBarIcon: ({ color, size }) => <Ionicons name="shield" size={size} color={color} />,
        }}
      />
    </Tabs>
  );
}
