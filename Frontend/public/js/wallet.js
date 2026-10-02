import { api } from './api.js';
import { errorMessage } from './messages.js';
import { showFormSuccess, submitWith } from './ui.js';

const balance = document.getElementById('wallet-balance');
const walletError = document.getElementById('wallet-error');
const amountForm = document.getElementById('amount-form');
const transferForm = document.getElementById('transfer-form');

let onChanged = () => {};

export function initWallet(changedHandler) {
    onChanged = changedHandler;

    amountForm.addEventListener('submit', event => {
        event.preventDefault();
        const submitter = event.submitter;
        const operation = submitter.value;
        const amount = Number(amountForm.elements.amount.value);
        submitWith(amountForm, async () => {
            const wallet = await api(`/wallets/me/${operation}`, { method: 'POST', body: { amount } });
            renderBalance(wallet);
            amountForm.reset();
            showFormSuccess(amountForm,
                operation === 'deposit' ? `Пополнено на ${formatAmount(amount)}` : `Снято ${formatAmount(amount)}`);
            onChanged();
        }, submitter);
    });

    transferForm.addEventListener('submit', event => {
        event.preventDefault();
        const toUsername = transferForm.elements.toUsername.value.trim();
        const amount = Number(transferForm.elements.amount.value);
        submitWith(transferForm, async () => {
            const wallet = await api('/wallets/me/transfer', { method: 'POST', body: { toUsername, amount } });
            renderBalance(wallet);
            transferForm.reset();
            showFormSuccess(transferForm, `Переведено ${formatAmount(amount)} пользователю ${toUsername}`);
            onChanged();
        });
    });
}

export async function loadWallet() {
    walletError.hidden = true;
    try {
        const [wallet, limits] = await Promise.all([api('/wallets/me'), api('/wallets/limits')]);
        renderBalance(wallet);
        applyLimit(limits.maxOperationAmount);
    } catch (error) {
        balance.textContent = '—';
        walletError.textContent = errorMessage(error);
        walletError.hidden = false;
    }
}

export function formatAmount(amount) {
    return `${amount.toLocaleString('ru-RU')} MNC`;
}

function renderBalance(wallet) {
    balance.textContent = wallet.balance.toLocaleString('ru-RU');
}

function applyLimit(maxAmount) {
    for (const form of [amountForm, transferForm]) {
        form.elements.amount.max = maxAmount;
        form.querySelector('.amount-hint').textContent = `От 1 до ${formatAmount(maxAmount)} за одну операцию`;
    }
}
