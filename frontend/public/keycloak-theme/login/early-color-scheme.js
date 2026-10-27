// Runs before the login page renders, so it opens in the app's theme without a flash:
// Keycloak is on the app's origin and sees the theme the app stored (src/store/themeStore.ts).
try {
  const stored = JSON.parse(localStorage.getItem('fate-theme') || 'null')
  if (stored && stored.state && stored.state.theme === 'light') {
    document.documentElement.classList.add('light')
  }
} catch {
  // No storage (private mode, blocked cookies): the dark default stays.
}
