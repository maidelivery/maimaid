export const MAX_PASSWORD_LENGTH = 128;

const PASSWORD_COMPLEXITY_PATTERN = new RegExp(
  `^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,${MAX_PASSWORD_LENGTH}}$`
);

export const PASSWORD_COMPLEXITY_ERROR_MESSAGE =
  `Password must be 8-${MAX_PASSWORD_LENGTH} characters and include lowercase, uppercase, digits, and symbols.`;

export const isPasswordComplexEnough = (password: string): boolean => {
  return PASSWORD_COMPLEXITY_PATTERN.test(password);
};
