export type AuthContext = {
  userId: string;
  email: string;
  isAdmin: boolean;
};

export type AppEnv = {
  Variables: {
    auth: AuthContext | undefined;
  };
};
