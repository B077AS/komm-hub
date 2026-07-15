/**
 * Shared auth helper for the hub web pages.
 *
 * The short-lived access token lives in sessionStorage (per tab); the
 * long-lived refresh token lives in an HttpOnly cookie scoped to /api/auth,
 * so page scripts can never read it. KommAuth.fetch transparently refreshes
 * the access token and retries once when a request comes back 401.
 */
const KommAuth = (() => {
    const API_BASE = window.location.origin;

    /** Exchanges the refresh cookie for a new access token. Returns it, or null. */
    async function refresh() {
        try {
            const resp = await fetch(API_BASE + '/api/auth/refresh', { method: 'POST' });
            if (!resp.ok) return null;
            const data = await resp.json();
            sessionStorage.setItem('komm_access_token', data.accessToken);
            return data.accessToken;
        } catch (e) {
            return null;
        }
    }

    /** This tab's access token, silently refreshing via the cookie if needed. */
    async function ensureSession() {
        return sessionStorage.getItem('komm_access_token') || await refresh();
    }

    /** fetch() with the Bearer header attached; on 401, refreshes and retries once. */
    async function authFetch(url, options = {}) {
        const doFetch = () => fetch(url, Object.assign({}, options, {
            headers: Object.assign({}, options.headers, {
                'Authorization': 'Bearer ' + sessionStorage.getItem('komm_access_token')
            })
        }));
        let resp = await doFetch();
        if (resp.status === 401 && await refresh()) resp = await doFetch();
        return resp;
    }

    /** Revokes the refresh token server-side, clears the cookie and this tab's session. */
    async function logout() {
        try {
            await fetch(API_BASE + '/api/auth/logout', { method: 'POST' });
        } catch (e) { /* best effort — local state is cleared regardless */ }
        ['komm_access_token', 'komm_refresh_token', 'komm_username']
            .forEach(k => sessionStorage.removeItem(k));
    }

    return { refresh, ensureSession, fetch: authFetch, logout };
})();
