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

export function showFormSuccess(form, text) {
    const message = form.querySelector('.form-success');
    message.textContent = text;
    message.hidden = false;
}

export function clearFormError(form) {
    form.querySelectorAll('.form-error, .form-success').forEach(message => {
        message.hidden = true;
        message.textContent = '';
    });
    form.querySelectorAll('[aria-invalid]').forEach(input => input.removeAttribute('aria-invalid'));
    form.querySelectorAll('.field-error').forEach(hint => hint.remove());
}

export async function submitWith(form, action, submitter) {
    const buttons = form.querySelectorAll('button[type="submit"]');
    const busyButton = submitter || buttons[0];
    clearFormError(form);
    buttons.forEach(button => {
        button.disabled = true;
    });
    busyButton.setAttribute('aria-busy', 'true');
    try {
        await action();
    } catch (error) {
        showFormError(form, error);
    } finally {
        buttons.forEach(button => {
            button.disabled = false;
        });
        busyButton.removeAttribute('aria-busy');
    }
}
