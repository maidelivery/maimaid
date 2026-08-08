package net.krtl.maimaid.core.domain

enum class SyncConflictPolicy {
    MERGE_LOCAL_AND_CLOUD,
    OVERWRITE_LOCAL_WITH_CLOUD,
    OVERWRITE_CLOUD_WITH_LOCAL
}

data class SyncPushResult(
    val latestRevision: String
)

data class SyncPullResult(
    val latestRevision: String,
    val profileCount: Int,
    val scoreCount: Int,
    val recordCount: Int
)

