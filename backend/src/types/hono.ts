import type { InjectionToken } from "tsyringe";

export type AuthContext = {
	userId: string;
	email: string;
	isAdmin: boolean;
};

export type AppEnv = {
	Bindings: {
		HYPERDRIVE?: {
			connectionString: string;
		};
		MAIMAID_D1?: unknown;
		MAIMAID_DATA?: unknown;
		[key: string]: unknown;
	};
	Variables: {
		auth: AuthContext | undefined;
		resolve: <T>(token: InjectionToken<T>) => T;
	};
};
