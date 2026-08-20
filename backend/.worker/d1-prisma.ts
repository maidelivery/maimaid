import { PrismaD1 } from "@prisma/adapter-d1";
import { PrismaClient } from "../generated/d1/edge.js";
import { D1SequenceStorage, type D1SequenceBinding } from "./d1-sequence.js";
import { withD1TransactionCompatibility } from "./d1-transaction.js";
import { R2JsonStorage, type R2JsonBucket } from "./r2-json.js";

export const createD1PrismaClient = (binding: D1SequenceBinding, bucket: R2JsonBucket) => {
	const adapter = new PrismaD1(binding as never);
	const sequenceStorage = new D1SequenceStorage(binding);
	const jsonStorage = new R2JsonStorage(bucket);
	const externalJsonQuery = {
		async $allOperations({ model, operation, args, query }: Parameters<
			Parameters<PrismaClient["$extends"]>[0]["query"]["$allModels"]["$allOperations"]
		>[0]) {
			const sequencedArgs = await sequenceStorage.prepareArgs(model, operation, args);
			const nextArgs = await jsonStorage.prepareArgs(model, operation, sequencedArgs);
			const result = await query(nextArgs as typeof args);
			return jsonStorage.hydrateResult(result);
		},
	};
	const client = new PrismaClient({ adapter }).$extends({
		query: {
			catalogSnapshot: externalJsonQuery,
			sheet: externalJsonQuery,
			alias: externalJsonQuery,
			syncEvent: externalJsonQuery,
			staticBundle: externalJsonQuery,
			chartFitSnapshot: externalJsonQuery,
			importRawPayload: externalJsonQuery,
			jobQueue: externalJsonQuery,
		},
	});
	return withD1TransactionCompatibility(client);
};
