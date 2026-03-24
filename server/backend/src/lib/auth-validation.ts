const PASSWORD_COMPLEXITY_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,}$/;

export const PASSWORD_COMPLEXITY_ERROR_MESSAGE =
  "Password must be at least 8 characters and include lowercase, uppercase, digits, and symbols.";

export const isPasswordComplexEnough = (password: string): boolean => {
  return PASSWORD_COMPLEXITY_PATTERN.test(password);
};

