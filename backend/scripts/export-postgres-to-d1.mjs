import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import pg from "pg";

const databaseUrl = process.env.MAIMAID_SOURCE_DATABASE_URL;
const outputDirectory = process.argv[2];
const schemaPath = process.argv[3];
if (!databaseUrl || !outputDirectory || !schemaPath) {
	throw new Error(
		"Usage: MAIMAID_SOURCE_DATABASE_URL=postgresql://... node scripts/export-postgres-to-d1.mjs <output-directory> <d1-schema.sql>",
	);
}

const migrationId = path.basename(outputDirectory);
const payloadDirectory = path.join(outputDirectory, "r2");
const dataDirectory = path.join(outputDirectory, "sql");
const maxStatementBytes = 80_000;
// D1 caps a single SQL statement at 100 KB. Keep JSON tuples below that limit
// with enough room for the row's remaining columns and SQL quoting overhead.
const externalPayloadBytes = 50_000;
const externalPayloadColumns = new Set([
	"catalog_snapshots.payloadJson",
	"static_bundles.payloadJson",
	"chart_fit_snapshots.payloadJson",
	"import_raw_payloads.payloadJson",
]);

await mkdir(payloadDirectory, { recursive: true });
await mkdir(dataDirectory, { recursive: true });

const sourceSchema = await readFile(schemaPath, "utf8");
const tableNames = [...sourceSchema.matchAll(/CREATE TABLE "([^"]+)"/gu)].map((match) => match[1]);

const quoteIdentifier = (value) => `"${value.replaceAll('"', '""')}"`;
const sqlLiteral = (value) => {
	if (value === null || value === undefined) return "NULL";
	if (typeof value === "boolean") return value ? "1" : "0";
	if (typeof value === "number" || typeof value === "bigint") return String(value);
	if (value instanceof Date) return `'${value.toISOString()}'`;
	if (Buffer.isBuffer(value)) return `X'${value.toString("hex")}'`;
	const text = typeof value === "object" ? JSON.stringify(value) : String(value);
	return `'${text.replaceAll("'", "''")}'`;
};
const digest = (value) => createHash("sha256").update(value).digest("hex");

const client = new pg.Client({ connectionString: databaseUrl });
await client.connect();
await client.query("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");

const manifest = {
	migrationId,
	createdAt: new Date().toISOString(),
	tables: {},
	externalPayloads: [],
};

try {
	for (const tableName of tableNames) {
		const columnsResult = await client.query(
			`SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = $1 ORDER BY ordinal_position`,
			[tableName],
		);
		const primaryKeyResult = await client.query(
			`SELECT a.attname AS column_name
			   FROM pg_index i
			   JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
			  WHERE i.indrelid = $1::regclass AND i.indisprimary
			  ORDER BY array_position(i.indkey, a.attnum)`,
			[`public.${tableName}`],
		);
		const columns = columnsResult.rows.map((row) => row.column_name);
		const primaryKeys = primaryKeyResult.rows.map((row) => row.column_name);
		const orderBy = primaryKeys.length > 0 ? ` ORDER BY ${primaryKeys.map(quoteIdentifier).join(", ")}` : "";
		const result = await client.query(`SELECT * FROM ${quoteIdentifier(tableName)}${orderBy}`);
		const statements = [];
		let currentValues = [];
		let currentBytes = 0;
		const prefix = `INSERT INTO ${quoteIdentifier(tableName)} (${columns.map(quoteIdentifier).join(", ")}) VALUES `;

		const flush = () => {
			if (currentValues.length === 0) return;
			statements.push(`${prefix}${currentValues.join(",\n")};`);
			currentValues = [];
			currentBytes = 0;
		};

		for (const sourceRow of result.rows) {
			const row = { ...sourceRow };
			const primaryKey =
				primaryKeys.map((column) => String(row[column])).join(":") || String(manifest.tables[tableName]?.rows ?? 0);
			for (const column of columns) {
				const field = `${tableName}.${column}`;
				const value = row[column];
				if (!externalPayloadColumns.has(field) || value === null || value === undefined) continue;
				const payload = typeof value === "string" ? value : JSON.stringify(value);
				const bytes = Buffer.byteLength(payload);
				if (bytes <= externalPayloadBytes) continue;
				const sha256 = digest(payload);
				const fileName = `${tableName}-${column}-${digest(primaryKey).slice(0, 24)}.json`;
				const objectKey = `postgres-migrations/${migrationId}/${fileName}`;
				await writeFile(path.join(payloadDirectory, fileName), payload);
				row[column] = { $r2: objectKey, sha256, bytes };
				manifest.externalPayloads.push({ table: tableName, column, primaryKey, objectKey, sha256, bytes, fileName });
			}

			const tuple = `(${columns.map((column) => sqlLiteral(row[column])).join(", ")})`;
			const tupleBytes = Buffer.byteLength(tuple);
			if (currentValues.length > 0 && currentBytes + tupleBytes + prefix.length > maxStatementBytes) flush();
			currentValues.push(tuple);
			currentBytes += tupleBytes + 2;
		}
		flush();

		const contents = statements.length > 0 ? `${statements.join("\n")}\n` : "-- Empty table.\n";
		const fileName = `${String(tableNames.indexOf(tableName) + 1).padStart(2, "0")}-${tableName}.sql`;
		await writeFile(path.join(dataDirectory, fileName), contents);
		manifest.tables[tableName] = {
			rows: result.rowCount,
			sha256: digest(contents),
			statements: statements.length,
			fileName,
		};
		console.log(`${tableName}: ${result.rowCount} rows, ${statements.length} statements`);
	}

	await client.query("COMMIT");
} catch (error) {
	await client.query("ROLLBACK");
	throw error;
} finally {
	await client.end();
}

const schemaWithManifest = `${sourceSchema}
CREATE TABLE IF NOT EXISTS "migration_manifests" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "createdAt" TEXT NOT NULL,
  "source" TEXT NOT NULL,
  "tableCountsJson" TEXT NOT NULL,
  "externalPayloadCount" INTEGER NOT NULL
);
`;
await writeFile(path.join(outputDirectory, "schema.sql"), schemaWithManifest);
await writeFile(path.join(outputDirectory, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
await writeFile(
	path.join(dataDirectory, "99-migration-manifest.sql"),
	`INSERT INTO "migration_manifests" ("id", "createdAt", "source", "tableCountsJson", "externalPayloadCount") VALUES (${sqlLiteral(migrationId)}, ${sqlLiteral(manifest.createdAt)}, 'postgresql', ${sqlLiteral(JSON.stringify(Object.fromEntries(Object.entries(manifest.tables).map(([name, value]) => [name, value.rows]))))}, ${manifest.externalPayloads.length});\n`,
);
console.log(
	`Exported ${tableNames.length} tables and ${manifest.externalPayloads.length} external payloads to ${outputDirectory}`,
);
