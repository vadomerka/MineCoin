import { api } from './api.js';
import { clearFormError, submitWith } from './ui.js';

const ROLE_NAMES = { USER: 'Пользователь', ADMIN: 'Администратор' };

const view = document.getElementById('profile-view');
const form = document.getElementById('profile-form');
const deleteDialog = document.getElementById('delete-dialog');
const deleteForm = document.getElementById('delete-form');

let currentProfile = null;

export function initProfile(onDeleted) {
    document.getElementById('edit-profile-button').addEventListener('click', showForm);
    document.getElementById('cancel-edit-button').addEventListener('click', showView);

    form.addEventListener('submit', event => {
        event.preventDefault();
        const { email, displayName } = Object.fromEntries(new FormData(form));
        submitWith(form, async () => {
            const profile = await api('/users/me', {
                method: 'PUT',
                body: { email, displayName: displayName || null },
            });
            renderProfile(profile);
            showView();
        });
    });

    document.getElementById('delete-account-button').addEventListener('click', () => {
        clearFormError(deleteForm);
        deleteDialog.showModal();
    });
    document.getElementById('cancel-delete-button').addEventListener('click', () => deleteDialog.close());

    deleteForm.addEventListener('submit', event => {
        event.preventDefault();
        submitWith(deleteForm, async () => {
            await api('/users/me', { method: 'DELETE' });
            deleteDialog.close();
            onDeleted();
        });
    });
}

export function renderProfile(profile) {
    currentProfile = profile;
    document.getElementById('profile-username').textContent = profile.username;
    document.getElementById('profile-email').textContent = profile.email;
    document.getElementById('profile-display-name').textContent = profile.displayName || '—';
    document.getElementById('profile-role').textContent = ROLE_NAMES[profile.role] || profile.role;
    document.getElementById('profile-created-at').textContent = new Date(profile.createdAt).toLocaleString('ru-RU');
    showView();
}

function showForm() {
    form.elements.email.value = currentProfile.email;
    form.elements.displayName.value = currentProfile.displayName || '';
    clearFormError(form);
    view.hidden = true;
    form.hidden = false;
}

function showView() {
    form.hidden = true;
    view.hidden = false;
}
