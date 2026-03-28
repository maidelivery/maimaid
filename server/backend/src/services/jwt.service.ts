import { SignJWT, jwtVerify } from "jose";
import { injectable, inject } from "tsyringe";
import type { Env } from "../env.js";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";

export type AccessTokenPayload = {
  sub: string;
  email: string;
  isAdmin: boolean;
};

@injectable()
export class JwtService {
  constructor(@inject(TOKENS.Env) private readonly env: Env) {}

  private accessSecret(): Uint8Array {
    return new TextEncoder().encode(this.env.JWT_ACCESS_SECRET);
  }

  async signAccessToken(payload: AccessTokenPayload): Promise<string> {
    return new SignJWT({
      email: payload.email,
      isAdmin: payload.isAdmin
    })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setSubject(payload.sub)
      .setIssuer(this.env.JWT_ISSUER)
      .setAudience(this.env.JWT_AUDIENCE)
      .setIssuedAt()
      .setExpirationTime(`${this.env.JWT_ACCESS_TTL_SECONDS}s`)
      .sign(this.accessSecret());
  }

  async verifyAccessToken(token: string): Promise<AccessTokenPayload> {
    try {
      const { payload } = await jwtVerify(token, this.accessSecret(), {
        issuer: this.env.JWT_ISSUER,
        audience: this.env.JWT_AUDIENCE
      });

      return {
        sub: String(payload.sub),
        email: String(payload.email),
        isAdmin: Boolean(payload.isAdmin)
      };
    } catch {
      throw new AppError(401, "unauthorized", "Invalid access token.");
    }
  }
}
