import { api, getToken, onUnauthorized } from './api.js';
import { initAuth, logout, showSessionMessage } from './auth.js';
import { errorMessage } from './messages.js';

const authScreen = document.getElementById('auth-screen');
const appScreen = document.getElementById('app-screen');
const userMenu = document.getElementById('user-menu');
const currentUsername = document.getElementById('current-username');
const greeting = document.getElementById('greeting');

function showAuth() {
    appScreen.hidden = true;
    userMenu.hidden = true;
    authScreen.hidden = false;
}

async function showApp() {
    try {
        const me = await api('/users/me');
        currentUsername.textContent = me.username;
        greeting.textContent = `Добро пожаловать, ${me.displayName || me.username}!`;
        authScreen.hidden = true;
        userMenu.hidden = false;
        appScreen.hidden = false;
    } catch (error) {
        showAuth();
        if (error.status !== 401) {
            showSessionMessage(errorMessage(error));
        }
    }
}

onUnauthorized(() => {
    showAuth();
    showSessionMessage(errorMessage({ code: 'UNAUTHORIZED' }));
});

initAuth(showApp);

document.getElementById('logout-button').addEventListener('click', () => {
    logout();
    showAuth();
});

if (getToken()) {
    showApp();
} else {
    showAuth();
}
