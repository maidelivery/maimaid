import "reflect-metadata";
import { container } from "tsyringe";
import { TOKENS } from "./tokens.js";
import { getEnv } from "../env.js";
import { getPrismaClient } from "../lib/prisma.js";
import { JwtService } from "../services/jwt.service.js";
import { AuthService } from "../services/auth.service.js";
import { ProfileService } from "../services/profile.service.js";
import { CatalogService } from "../services/catalog.service.js";
import { ScoreService } from "../services/score.service.js";
import { ImportService } from "../services/import.service.js";
import { CommunityAliasService } from "../services/community-alias.service.js";
import { StorageService } from "../services/storage.service.js";
import { JobService } from "../services/job.service.js";

const env = getEnv();
const prisma = getPrismaClient();

container.register(TOKENS.Env, { useValue: env });
container.register(TOKENS.Prisma, { useValue: prisma });
container.registerSingleton(TOKENS.JwtService, JwtService);
container.registerSingleton(TOKENS.StorageService, StorageService);
container.registerSingleton(TOKENS.AuthService, AuthService);
container.registerSingleton(TOKENS.ProfileService, ProfileService);
container.registerSingleton(TOKENS.CatalogService, CatalogService);
container.registerSingleton(TOKENS.ScoreService, ScoreService);
container.registerSingleton(TOKENS.ImportService, ImportService);
container.registerSingleton(TOKENS.CommunityAliasService, CommunityAliasService);
container.registerSingleton(TOKENS.JobService, JobService);

export const di = container;
