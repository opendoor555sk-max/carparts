// Design tokens — "Dark-First Utility DARK" industrial command-center theme.
// No blues/purples. Amber accent on obsidian. Flat surfaces, hard borders, no shadows.

export const colors = {
  surface: "#121212",
  onSurface: "#FFFFFF",
  surface2: "#1E1E1E",
  onSurface2: "#E0E0E0",
  surface3: "#2C2C2C",
  onSurface3: "#BDBDBD",
  brand: "#FF8A00",
  onBrand: "#000000",
  brandDim: "#CC6E00",
  brandFaint: "#4D2900",
  onBrandFaint: "#FFB84D",
  success: "#34C759",
  onSuccess: "#000000",
  warning: "#FFCC00",
  onWarning: "#000000",
  error: "#FF3B30",
  onError: "#FFFFFF",
  info: "#9E9E9E",
  onInfo: "#000000",
  border: "#333333",
  borderStrong: "#4F4F4F",
  divider: "#2A2A2A",
};

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, xxxl: 48 };
export const radius = { sm: 6, md: 12, lg: 20, pill: 999 };
export const font = { sm: 12, base: 14, lg: 16, xl: 20, xxl: 24, huge: 34 };

// Status → color mapping used across the app.
export const statusColor = (status: string): { bg: string; fg: string; border: string } => {
  switch (status) {
    case "IN STOCK":
    case "Working":
    case "Verified":
    case "Approved":
    case "Completed":
    case "OK TO BUY":
    case "BUY — REQUIRED":
      return { bg: colors.brandFaint, fg: colors.success, border: colors.success };
    case "REQUIREMENT":
    case "Pending":
    case "WARNING":
    case "BUY WITH CAUTION":
    case "Requires Verification":
    case "Testing":
      return { bg: "#3a3300", fg: colors.warning, border: colors.warning };
    case "NEW PART":
    case "STOP":
    case "DO NOT BUY":
    case "Rejected":
    case "Cancelled":
    case "Damaged":
    case "Scrap":
      return { bg: "#3a0f0d", fg: colors.error, border: colors.error };
    case "KNOWN PART":
    case "IN CATALOG":
    case "Unverified":
    case "ALREADY IN STOCK":
    default:
      return { bg: colors.surface3, fg: colors.info, border: colors.borderStrong };
  }
};
