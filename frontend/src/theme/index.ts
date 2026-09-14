// Design tokens — "Trusted Blue" light, professional theme for Play Store
// publishing. Soft off-white surfaces, dark-gray (never pure black) text,
// a blue primary for trust/professionalism, and consistent semantic colors
// (green=success, amber=warning/low-stock, red=error/damaged) used the same
// way everywhere. Subtle elevation (shadow) on cards instead of hard borders
// doing all the work.
//
// Token NAMES are intentionally unchanged from the previous dark theme
// (surface/onSurface/brand/success/etc.) — every screen already consumes
// these semantically, so this file is the single place the whole app's look
// flows from.

export const colors = {
  // Base surfaces: `surface` is the screen background, `surface2` is the
  // elevated layer (cards, header, tab bar, inputs), `surface3` is a step
  // further in (tracks, inactive chips, nested wells).
  surface: "#F4F6F9",
  onSurface: "#1E293B",
  surface2: "#FFFFFF",
  onSurface2: "#334155",
  surface3: "#ECEFF3",
  onSurface3: "#64748B",

  // Primary brand — blue, for trust/professionalism.
  brand: "#2563EB",
  onBrand: "#FFFFFF",
  brandDim: "#1D4ED8",
  brandFaint: "#DBEAFE",
  onBrandFaint: "#1E40AF",

  success: "#16A34A",
  onSuccess: "#FFFFFF",
  successFaint: "#DCFCE7",
  onSuccessFaint: "#15803D",

  warning: "#D97706",
  onWarning: "#7C2D12",
  warningFaint: "#FEF3C7",
  onWarningFaint: "#92400E",

  error: "#DC2626",
  onError: "#FFFFFF",
  errorFaint: "#FEE2E2",
  onErrorFaint: "#991B1B",

  // Neutral/informational — secondary text, dims, "unknown" status.
  info: "#64748B",
  onInfo: "#FFFFFF",

  border: "#E2E8F0",
  borderStrong: "#CBD5E1",
  divider: "#EDF1F5",
};

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, xxxl: 48 };
export const radius = { sm: 6, md: 12, lg: 20, pill: 999 };
export const font = { sm: 12, base: 14, lg: 16, xl: 20, xxl: 24, huge: 34 };

// Elevation — subtle, consistent shadow steps (iOS shadow* + Android
// elevation together) used in place of the old flat/hard-border look. `sm`
// for cards and list rows, `md` for floating action buttons and the sticky
// header, `lg` for modals/sheets.
export const shadow = {
  sm: {
    shadowColor: "#0F172A",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 3,
    elevation: 2,
  },
  md: {
    shadowColor: "#0F172A",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.10,
    shadowRadius: 8,
    elevation: 4,
  },
  lg: {
    shadowColor: "#0F172A",
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.16,
    shadowRadius: 24,
    elevation: 10,
  },
};

// Status → color mapping used across the app. Each bucket gets a matched
// faint tint + solid foreground + border from the SAME semantic family
// (never a mismatched pairing like a warning tint with a success color).
export const statusColor = (status: string): { bg: string; fg: string; border: string } => {
  switch (status) {
    case "IN STOCK":
    case "Working":
    case "Verified":
    case "Approved":
    case "Completed":
    case "OK TO BUY":
    case "BUY — REQUIRED":
      return { bg: colors.successFaint, fg: colors.onSuccessFaint, border: colors.success };
    case "REQUIREMENT":
    case "Pending":
    case "WARNING":
    case "BUY WITH CAUTION":
    case "Requires Verification":
    case "Testing":
      return { bg: colors.warningFaint, fg: colors.onWarningFaint, border: colors.warning };
    case "NEW PART":
    case "STOP":
    case "DO NOT BUY":
    case "Rejected":
    case "Cancelled":
    case "Damaged":
    case "Scrap":
      return { bg: colors.errorFaint, fg: colors.onErrorFaint, border: colors.error };
    case "KNOWN PART":
    case "IN CATALOG":
    case "Unverified":
    case "ALREADY IN STOCK":
    default:
      return { bg: colors.surface3, fg: colors.onSurface3, border: colors.borderStrong };
  }
};
