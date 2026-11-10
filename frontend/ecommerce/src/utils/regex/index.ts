export const lowerCharacterRegex = new RegExp(/(?=.*[a-z])/);
export const upperCharacterRegex = new RegExp(/(?=.*[A-Z])/);
export const numberCharacterRegex = new RegExp(/(?=.*[0-9])/);
export const specialCharacterRegex = new RegExp(/(?=.*[!@#$%^&*+<>=-])/);
export const lengthRegex = new RegExp(/^(?=.{8,32}$)/);

export const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,32}$/