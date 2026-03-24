import { GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { inject, injectable } from "tsyringe";
import type { Env } from "../env.js";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";

@injectable()
export class StorageService {
  private readonly client: S3Client | null;
  private readonly signingClient: S3Client | null;

  constructor(@inject(TOKENS.Env) private readonly env: Env) {
    const endpoint = env.S3_ENDPOINT;
    const signingEndpoint = env.S3_PUBLIC_ENDPOINT ?? endpoint;
    const accessKeyId = env.S3_ACCESS_KEY_ID;
    const secretAccessKey = env.S3_SECRET_ACCESS_KEY;
    if (!endpoint || !signingEndpoint || !accessKeyId || !secretAccessKey) {
      this.client = null;
      this.signingClient = null;
      return;
    }

    const baseConfig = {
      region: env.S3_REGION,
      credentials: {
        accessKeyId,
        secretAccessKey
      },
      forcePathStyle: true
    } as const;

    this.client = new S3Client({
      endpoint,
      ...baseConfig
    });
    this.signingClient = new S3Client({
      endpoint: signingEndpoint,
      ...baseConfig
    });
  }

  async createAvatarUploadUrl(profileId: string, contentType: string): Promise<{ key: string; uploadUrl: string }> {
    if (!this.signingClient) {
      throw new AppError(500, "storage_not_configured", "S3 storage is not configured.");
    }
    const key = `avatars/profiles/${profileId}`;
    const command = new PutObjectCommand({
      Bucket: this.env.S3_BUCKET,
      Key: key,
      ContentType: contentType
    });

    const uploadUrl = await getSignedUrl(this.signingClient, command, { expiresIn: 300 });
    return { key, uploadUrl };
  }

  async getObject(
    key: string
  ): Promise<{ data: Uint8Array; contentType: string | null; etag: string | null; lastModified: Date | null }> {
    if (!this.client) {
      throw new AppError(500, "storage_not_configured", "S3 storage is not configured.");
    }

    try {
      const result = await this.client.send(
        new GetObjectCommand({
          Bucket: this.env.S3_BUCKET,
          Key: key
        })
      );

      if (!result.Body) {
        throw new AppError(404, "avatar_not_found", "Avatar object not found.");
      }

      const data = await this.toUint8Array(result.Body);
      return {
        data,
        contentType: result.ContentType ?? null,
        etag: result.ETag ?? null,
        lastModified: result.LastModified ?? null
      };
    } catch (error) {
      const statusCode = this.readHttpStatus(error);
      if (statusCode === 404) {
        throw new AppError(404, "avatar_not_found", "Avatar object not found.");
      }
      throw error;
    }
  }

  private async toUint8Array(body: unknown): Promise<Uint8Array> {
    if (body instanceof Uint8Array) {
      return body;
    }
    if (typeof body === "string") {
      return new TextEncoder().encode(body);
    }
    if (body instanceof ArrayBuffer) {
      return new Uint8Array(body);
    }

    if (
      typeof body === "object" &&
      body !== null &&
      "transformToByteArray" in body &&
      typeof body.transformToByteArray === "function"
    ) {
      return await body.transformToByteArray();
    }

    if (this.isAsyncIterable(body)) {
      const chunks: Uint8Array[] = [];
      for await (const chunk of body) {
        if (chunk instanceof Uint8Array) {
          chunks.push(chunk);
          continue;
        }

        if (typeof chunk === "string") {
          chunks.push(new TextEncoder().encode(chunk));
          continue;
        }

        if (chunk instanceof ArrayBuffer) {
          chunks.push(new Uint8Array(chunk));
        }
      }

      const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
      const merged = new Uint8Array(totalLength);
      let offset = 0;
      for (const chunk of chunks) {
        merged.set(chunk, offset);
        offset += chunk.length;
      }
      return merged;
    }

    throw new AppError(500, "storage_stream_error", "Unsupported storage body stream type.");
  }

  private isAsyncIterable(value: unknown): value is AsyncIterable<unknown> {
    return typeof value === "object" && value !== null && Symbol.asyncIterator in value;
  }

  private readHttpStatus(error: unknown): number | null {
    if (typeof error !== "object" || error === null || !("$metadata" in error)) {
      return null;
    }

    const metadata = error.$metadata;
    if (typeof metadata !== "object" || metadata === null || !("httpStatusCode" in metadata)) {
      return null;
    }

    const status = metadata.httpStatusCode;
    return typeof status === "number" ? status : null;
  }
}
