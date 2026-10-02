import { errorMessage, fieldHint } from './messages.js';

export function showFormError(form, error) {
    const message = form.querySelector('.form-error');
    message.textContent = errorMessage(error);
    message.hidden = false;
    const fields = new Set(error.errors.map(({ field }) => field));
    for (const field of fields) {
        const input = form.elements[field];
        if (input) {
            input.setAttribute('aria-invalid', 'true');
            const hint = document.createElement('small');
            hint.className = 'field-error';
            hint.textContent = fieldHint(field);
            input.after(hint);
        }
    }
}

export function clearFormError(form) {
    const message = form.querySelector('.form-error');
    message.hidden = true;
    message.textContent = '';
    form.querySelectorAll('[aria-invalid]').forEach(input => input.removeAttribute('aria-invalid'));
    form.querySelectorAll('.field-error').forEach(hint => hint.remove());
}

export async function submitWith(form, action) {
    const button = form.querySelector('button[type="submit"]');
    clearFormError(form);
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    try {
        await action();
    } catch (error) {
        showFormError(form, error);
    } finally {
        button.disabled = false;
        button.removeAttribute('aria-busy');
    }
}
