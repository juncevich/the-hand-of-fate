/** Reads the emails the backend sent, to follow their links like a user would. */
const MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:30025'

interface Summary { ID: string }

async function json<T>(path: string): Promise<T> {
  const response = await fetch(`${MAILPIT}${path}`)
  if (!response.ok) throw new Error(`Mailpit ${path}: HTTP ${response.status}`)
  return (await response.json()) as T
}

/**
 * Waits for the newest email to `to` with a link whose path is `path` (e.g. "/verify-email")
 * and returns that link, relative to the app (path + query), so it opens on the tested origin.
 */
export async function mailedLink(to: string, path: string, timeoutMs = 30_000): Promise<string> {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const { messages } = await json<{ messages: Summary[] }>(`/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`)
    for (const { ID } of messages) {
      const { HTML } = await json<{ HTML: string }>(`/api/v1/message/${encodeURIComponent(ID)}`)
      for (const [, href] of HTML.matchAll(/href="([^"]+)"/g)) {
        const url = new URL(href.replaceAll('&amp;', '&'))
        if (url.pathname === path) return url.pathname + url.search
      }
    }
    if (Date.now() > deadline) throw new Error(`No email to ${to} linking to ${path} within ${timeoutMs} ms`)
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
}
