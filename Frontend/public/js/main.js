import { api, getToken, onUnauthorized } from './api.js';
import { initAuth, logout, showSessionMessage } from './auth.js';
import { errorMessage } from './messages.js';
import { initProfile, renderProfile } from './profile.js';

const authScreen = document.getElementById('auth-screen');
const appScreen = document.getElementById('app-screen');
const userMenu = document.getElementById('user-menu');
const currentUsername = document.getElementById('current-username');

function showAuth() {
    document.querySelectorAll('dialog[open]').forEach(dialog => dialog.close());
    appScreen.hidden = true;
    userMenu.hidden = true;
    authScreen.hidden = false;
}

async function showApp() {
    try {
        const me = await api('/users/me');
        currentUsername.textContent = me.username;
        renderProfile(me);
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

function signOut() {
    logout();
    showAuth();
}

onUnauthorized(() => {
    showAuth();
    showSessionMessage(errorMessage({ code: 'UNAUTHORIZED' }));
});

initAuth(showApp);
initProfile(signOut);

document.getElementById('logout-button').addEventListener('click', signOut);

if (getToken()) {
    showApp();
} else {
    showAuth();
}
