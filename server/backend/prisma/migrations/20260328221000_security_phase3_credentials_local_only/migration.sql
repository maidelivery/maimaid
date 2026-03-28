drop index if exists "profiles_dfImportToken_idx";

alter table "profiles"
  drop column if exists "dfImportToken",
  drop column if exists "lxnsRefreshToken",
  drop column if exists "lxnsClientId";
