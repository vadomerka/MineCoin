const ERROR_MESSAGES = {
    INVALID_CREDENTIALS: 'Неверный логин или пароль',
    USER_BLOCKED: 'Аккаунт заблокирован',
    USERNAME_TAKEN: 'Этот логин уже занят',
    EMAIL_TAKEN: 'Этот email уже используется',
    USER_NOT_FOUND: 'Пользователь не найден',
    VALIDATION_ERROR: 'Проверьте правильность заполнения полей',
    UNAUTHORIZED: 'Сессия истекла, войдите снова',
    FORBIDDEN: 'Недостаточно прав',
    INSUFFICIENT_FUNDS: 'Недостаточно средств на кошельке',
    AMOUNT_LIMIT_EXCEEDED: 'Сумма превышает лимит одной операции',
    SELF_TRANSFER: 'Нельзя перевести деньги самому себе',
    RECIPIENT_NOT_FOUND: 'Получатель не найден',
    USER_SERVICE_UNAVAILABLE: 'Сервис пользователей недоступен, попробуйте позже',
    SERVICE_UNAVAILABLE: 'Сервис временно недоступен, попробуйте позже',
    NETWORK_ERROR: 'Нет связи с сервером',
    INTERNAL_ERROR: 'Внутренняя ошибка сервера',
};

const FIELD_HINTS = {
    username: 'Логин: от 3 до 32 символов, латинские буквы, цифры и _',
    email: 'Укажите корректный email',
    password: 'Пароль: от 8 до 72 символов',
    displayName: 'Имя: не длиннее 64 символов',
    amount: 'Укажите целую сумму больше нуля',
    toUsername: 'Укажите логин получателя',
};

export function errorMessage(error) {
    return ERROR_MESSAGES[error.code] || error.message || 'Неизвестная ошибка';
}

export function fieldHint(field) {
    return FIELD_HINTS[field] || 'Некорректное значение';
}
