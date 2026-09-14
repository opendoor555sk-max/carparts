import React, { createContext, useCallback, useContext, useEffect, useState } from "react";

import { storage } from "@/src/utils/storage";
import { DEFAULT_LANGUAGE, Language, TranslationKey, translate, translateStatus } from "@/src/i18n/translations";

const STORAGE_KEY = "kabadi.language";

type LanguageCtx = {
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: TranslationKey) => string;
  tStatus: (status: string) => string;
};

const Ctx = createContext<LanguageCtx>({
  language: DEFAULT_LANGUAGE,
  setLanguage: () => {},
  t: (key) => translate(DEFAULT_LANGUAGE, key),
  tStatus: (status) => translateStatus(DEFAULT_LANGUAGE, status),
});

export const useLanguage = () => useContext(Ctx);

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguageState] = useState<Language>(DEFAULT_LANGUAGE);

  useEffect(() => {
    (async () => {
      const saved = await storage.getItem<Language>(STORAGE_KEY, DEFAULT_LANGUAGE);
      if (saved) setLanguageState(saved);
    })();
  }, []);

  const setLanguage = useCallback((lang: Language) => {
    setLanguageState(lang);
    storage.setItem(STORAGE_KEY, lang);
  }, []);

  const t = useCallback((key: TranslationKey) => translate(language, key), [language]);
  const tStatus = useCallback((status: string) => translateStatus(language, status), [language]);

  return <Ctx.Provider value={{ language, setLanguage, t, tStatus }}>{children}</Ctx.Provider>;
}
