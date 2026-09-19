// Client-side mirror of backend/server.py's OWNER_CONTACT default — used
// ONLY to decide whether to show the Owner Panel entry point/screen at all.
// This is a UI-visibility convenience, never an authorization check: every
// /owner/* call is independently re-verified server-side via is_owner()
// against the caller's own DB record, so a client faking this value gains
// nothing beyond seeing a button that then 403s.
export const OWNER_CONTACT = "+919773041676";
