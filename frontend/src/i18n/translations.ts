// Minimal hand-rolled i18n — no library. Investigated first (per the task):
// no i18n package (react-i18next/expo-localization/react-native-localize) is
// a dependency, and none is needed here — this is a plain object + string
// lookup, which is pure JS/TS with zero native surface. Given the scope
// (label/button strings only, 3 languages) a library would add dependency
// weight and, worse, real native-module risk (react-native-localize links
// native code; even expo-localization's autolinked native module wouldn't
// exist in the already-built APK) for no benefit over a lookup table — and
// the EAS build quota is exhausted, so nothing here may require a rebuild.
// This file only ever adds/edits plain JS objects — safe for OTA.

export type Language = "gu" | "hi" | "en";

export const LANGUAGES: { code: Language; label: string }[] = [
  { code: "gu", label: "ગુજરાતી" },
  { code: "hi", label: "हिंदी" },
  { code: "en", label: "English" },
];

export const DEFAULT_LANGUAGE: Language = "gu";

// Keys are grouped by screen for now (home.*, tabs.*, admin.*) — add more
// groups the same way to extend coverage; nothing else needs to change.
const dict = {
  gu: {
    "tabs.home": "હોમ",
    "tabs.inventory": "ઈન્વેન્ટરી",
    "tabs.needs": "જરૂરિયાત",
    "tabs.catalog": "કેટલોગ",
    "tabs.admin": "એડમિન",

    "home.companyGate": "કંપની ગેટ",
    "home.modules": "મોડ્યુલ્સ",
    "home.reports": "રિપોર્ટ્સ",
    "home.hint": "SEARCH, BUY, SELL ક્યારેય ભેગા થતા નથી — દરેક અલગ મોડ્યુલ છે. Primary ID = Part Number.",
    "module.search": "શોધ",
    "module.buy": "ખરીદી",
    "module.sell": "વેચાણ",
    "module.requirement": "જરૂરિયાત",
    "module.customers": "ગ્રાહકો",
    "module.vendors": "વિક્રેતાઓ",
    "module.damaged-returns": "ડેમેજ / રિટર્ન",
    "module.arrange": "સ્ટોર ગોઠવણ",

    "admin.title": "એડમિન પેનલ",
    "admin.statistics": "આંકડા",
    "admin.management": "મેનેજમેન્ટ",
    "admin.account": "એકાઉન્ટ",
    "admin.language": "ભાષા",
    "admin.languageSub": "એપ્લિકેશનની ભાષા પસંદ કરો",
  },
  hi: {
    "tabs.home": "होम",
    "tabs.inventory": "इन्वेंटरी",
    "tabs.needs": "ज़रूरतें",
    "tabs.catalog": "कैटलॉग",
    "tabs.admin": "एडमिन",

    "home.companyGate": "कंपनी गेट",
    "home.modules": "मॉड्यूल",
    "home.reports": "रिपोर्ट्स",
    "home.hint": "SEARCH, BUY, SELL कभी मिक्स नहीं होते — हर एक अलग मॉड्यूल है। Primary ID = Part Number.",
    "module.search": "खोजें",
    "module.buy": "खरीदें",
    "module.sell": "बेचें",
    "module.requirement": "ज़रूरत",
    "module.customers": "ग्राहक",
    "module.vendors": "विक्रेता",
    "module.damaged-returns": "डैमेज / रिटर्न",
    "module.arrange": "स्टोर व्यवस्था",

    "admin.title": "एडमिन पैनल",
    "admin.statistics": "आंकड़े",
    "admin.management": "मैनेजमेंट",
    "admin.account": "खाता",
    "admin.language": "भाषा",
    "admin.languageSub": "ऐप की भाषा चुनें",
  },
  en: {
    "tabs.home": "Home",
    "tabs.inventory": "Inventory",
    "tabs.needs": "Needs",
    "tabs.catalog": "Catalog",
    "tabs.admin": "Admin",

    "home.companyGate": "Company Gate",
    "home.modules": "Modules",
    "home.reports": "Reports",
    "home.hint": "SEARCH, BUY, SELL are never mixed — each is a separate module. Primary ID = Part Number.",
    "module.search": "Search",
    "module.buy": "Buy",
    "module.sell": "Sell",
    "module.requirement": "Requirement",
    "module.customers": "Customers",
    "module.vendors": "Vendors",
    "module.damaged-returns": "Damaged / Returns",
    "module.arrange": "Store Arrangement",

    "admin.title": "Admin Panel",
    "admin.statistics": "Statistics",
    "admin.management": "Management",
    "admin.account": "Account",
    "admin.language": "Language",
    "admin.languageSub": "Choose the app's language",
  },
} as const;

export type TranslationKey = keyof (typeof dict)["en"];

export function translate(lang: Language, key: TranslationKey): string {
  return dict[lang]?.[key] ?? dict.en[key] ?? key;
}
