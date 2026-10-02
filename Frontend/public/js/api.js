const TOKEN_KEY = 'minecoin.token';

let unauthorizedHandler = () => {};

export class ApiError extends Error {
    constructor(status, problem) {
        super(problem.detail || `HTTP ${status}`);
        this.status = status;
        this.code = problem.code || codeForStatus(status);
        this.errors = problem.errors || [];
    }
}

export function getToken() {
    try {
        return localStorage.getItem(TOKEN_KEY);
    } catch {
        return null;
    }
}

export function setToken(token) {
    try {
        localStorage.setItem(TOKEN_KEY, token);
    } catch {
    }
}

export function clearToken() {
    try {
        localStorage.removeItem(TOKEN_KEY);
    } catch {
    }
}

export function onUnauthorized(handler) {
    unauthorizedHandler = handler;
}

export async function api(path, { method = 'GET', body } = {}) {
    const token = getToken();
    const headers = { Accept: 'application/json' };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    let response;
    try {
        response = await fetch(`/api${path}`, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new ApiError(0, { code: 'NETWORK_ERROR' });
    }

    if (response.status === 401 && token) {
        clearToken();
        unauthorizedHandler();
    }
    if (!response.ok) {
        throw new ApiError(response.status, await readJson(response));
    }
    if (response.status === 204) {
        return null;
    }
    return response.json();
}

async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

function codeForStatus(status) {
    if (status === 502 || status === 503 || status === 504) {
        return 'SERVICE_UNAVAILABLE';
    }
    return 'UNKNOWN_ERROR';
}
