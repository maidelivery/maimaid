import type { Context } from "hono";
import type { ContentfulStatusCode } from "hono/utils/http-status";

const toJsonSafe = <T>(data: T): T =>
  JSON.parse(
    JSON.stringify(data, (_, value: unknown) => (typeof value === "bigint" ? value.toString() : value))
  ) as T;

export const ok = <T>(c: Context, data: T, status: ContentfulStatusCode = 200) => {
  return c.json(toJsonSafe(data), status);
};
