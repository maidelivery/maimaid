export const TOKENS = {
  Env: Symbol.for("Env"),
  Prisma: Symbol.for("Prisma"),
  JwtService: Symbol.for("JwtService"),
  AuthService: Symbol.for("AuthService"),
  ProfileService: Symbol.for("ProfileService"),
  CatalogService: Symbol.for("CatalogService"),
  ScoreService: Symbol.for("ScoreService"),
  ImportService: Symbol.for("ImportService"),
  CommunityAliasService: Symbol.for("CommunityAliasService"),
  AdminService: Symbol.for("AdminService"),
  StorageService: Symbol.for("StorageService"),
  JobService: Symbol.for("JobService"),
  MfaService: Symbol.for("MfaService"),
  RateLimitService: Symbol.for("RateLimitService"),
  SyncService: Symbol.for("SyncService"),
  StaticBundleService: Symbol.for("StaticBundleService"),
  AdminUserService: Symbol.for("AdminUserService")
} as const;
