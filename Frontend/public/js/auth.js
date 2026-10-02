import { api, clearToken, setToken } from './api.js';
import { submitWith } from './ui.js';

const loginCard = document.getElementById('login-card');
const registerCard = document.getElementById('register-card');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');

export function initAuth(onLoggedIn) {
    document.getElementById('show-register').addEventListener('click', event => {
        event.preventDefault();
        showRegister();
    });
    document.getElementById('show-login').addEventListener('click', event => {
        event.preventDefault();
        showLogin();
    });

    loginForm.addEventListener('submit', event => {
        event.preventDefault();
        const { username, password } = Object.fromEntries(new FormData(loginForm));
        submitWith(loginForm, async () => {
            await login(username, password);
            loginForm.reset();
            onLoggedIn();
        });
    });

    registerForm.addEventListener('submit', event => {
        event.preventDefault();
        const { username, email, password, displayName } = Object.fromEntries(new FormData(registerForm));
        submitWith(registerForm, async () => {
            await api('/auth/register', {
                method: 'POST',
                body: { username, email, password, displayName: displayName || null },
            });
            await login(username, password);
            registerForm.reset();
            showLogin();
            onLoggedIn();
        });
    });
}

export function logout() {
    clearToken();
}

export function showSessionMessage(text) {
    showLogin();
    const message = loginForm.querySelector('.form-error');
    message.textContent = text;
    message.hidden = false;
}

async function login(username, password) {
    const token = await api('/auth/login', { method: 'POST', body: { username, password } });
    setToken(token.accessToken);
}

function showLogin() {
    registerCard.hidden = true;
    loginCard.hidden = false;
}

function showRegister() {
    loginCard.hidden = true;
    registerCard.hidden = false;
}
