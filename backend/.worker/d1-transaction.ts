type InteractiveTransaction = (client: unknown) => Promise<unknown>;

export const withD1TransactionCompatibility = <T extends object>(client: T): T => {
	const proxy = new Proxy(client, {
		get(target, property, receiver) {
			const value = Reflect.get(target, property, receiver) as unknown;
			if (property !== "$transaction") {
				return typeof value === "function" ? value.bind(target) : value;
			}
			if (typeof value !== "function") {
				throw new Error("Prisma transaction method is unavailable.");
			}
			return async (operation: unknown, ...options: unknown[]) => {
				if (typeof operation === "function") {
					return (operation as InteractiveTransaction)(proxy);
				}
				return Reflect.apply(value, target, [operation, ...options]) as Promise<unknown>;
			};
		},
	});
	return proxy;
};
