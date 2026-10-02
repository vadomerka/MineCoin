import { getToken, onUnauthorized } from './api.js';
import { initAuth, logout, showSessionMessage } from './auth.js';
import { initHistory, loadHistory } from './history.js';
import { errorMessage } from './messages.js';
import { initProfile, loadProfile } from './profile.js';
import { initWallet, loadWallet } from './wallet.js';

const loading = document.getElementById('loading');
const authScreen = document.getElementById('auth-screen');
const appScreen = document.getElementById('app-screen');
const userMenu = document.getElementById('user-menu');
const currentUsername = document.getElementById('current-username');

function showAuth() {
    document.querySelectorAll('dialog[open]').forEach(dialog => dialog.close());
    loading.hidden = true;
    appScreen.hidden = true;
    userMenu.hidden = true;
    authScreen.hidden = false;
}

async function showApp() {
    authScreen.hidden = true;
    appScreen.hidden = true;
    currentUsername.textContent = '';
    loading.hidden = false;
    await Promise.all([loadProfile(), loadWallet(), loadHistory()]);
    if (!getToken()) {
        return;
    }
    loading.hidden = true;
    userMenu.hidden = false;
    appScreen.hidden = false;
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
initProfile(signOut, profile => {
    currentUsername.textContent = profile.username;
});
initWallet(loadHistory);
initHistory();

document.getElementById('logout-button').addEventListener('click', signOut);

if (getToken()) {
    showApp();
} else {
    showAuth();
}
