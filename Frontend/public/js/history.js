import { api } from './api.js';
import { errorMessage } from './messages.js';
import { formatAmount } from './wallet.js';

const PAGE_SIZE = 20;
const DELETED_PREFIX = '~deleted-';

const table = document.getElementById('history-table');
const rows = document.getElementById('history-rows');
const empty = document.getElementById('history-empty');
const errorText = document.getElementById('history-error');
const moreButton = document.getElementById('history-more');

let nextPage = 0;

export function initHistory() {
    moreButton.addEventListener('click', loadNextPage);
}

export async function loadHistory() {
    nextPage = 0;
    rows.replaceChildren();
    await loadNextPage();
}

async function loadNextPage() {
    errorText.hidden = true;
    moreButton.disabled = true;
    try {
        const page = await api(`/wallets/me/operations?page=${nextPage}&size=${PAGE_SIZE}`);
        rows.append(...page.items.map(toRow));
        nextPage += 1;
        table.hidden = page.totalElements === 0;
        empty.hidden = page.totalElements > 0;
        moreButton.hidden = nextPage >= page.totalPages;
    } catch (error) {
        errorText.textContent = errorMessage(error);
        errorText.hidden = false;
    } finally {
        moreButton.disabled = false;
    }
}

function toRow(operation) {
    const incoming = operation.direction === 'IN';
    const amount = cell(`${incoming ? '+' : '−'}${formatAmount(operation.amount)}`);
    amount.className = incoming ? 'amount-in' : 'amount-out';
    const row = document.createElement('tr');
    row.append(
        cell(new Date(operation.createdAt).toLocaleString('ru-RU')),
        cell(operationName(operation)),
        amount,
        cell(counterparty(operation)),
    );
    return row;
}

function cell(text) {
    const td = document.createElement('td');
    td.textContent = text;
    return td;
}

function operationName(operation) {
    switch (operation.type) {
        case 'DEPOSIT':
            return 'Пополнение';
        case 'WITHDRAW':
            return 'Снятие';
        default:
            return operation.direction === 'IN' ? 'Входящий перевод' : 'Перевод';
    }
}

function counterparty(operation) {
    if (!operation.counterpartyId) {
        return '—';
    }
    if (!operation.counterpartyUsername) {
        return 'неизвестно';
    }
    if (operation.counterpartyUsername.startsWith(DELETED_PREFIX)) {
        return 'удалённый пользователь';
    }
    return operation.counterpartyUsername;
}
