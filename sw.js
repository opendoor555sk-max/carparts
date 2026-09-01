// Minimal service worker — required for PWA/Android packaging.
// Passes all requests straight through to the network (no offline caching),
// since this app relies on live camera/GPS/network features anyway.
self.addEventListener('install', (e) => {
  self.skipWaiting();
});
self.addEventListener('activate', (e) => {
  self.clients.claim();
});
self.addEventListener('fetch', (e) => {
  e.respondWith(fetch(e.request));
});
