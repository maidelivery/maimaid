package net.krtl.maimaid.ui.navigation

import android.net.Uri

sealed class AppRoute(val route: String) {
    data object Home : AppRoute("home")
    data object Search : AppRoute("search")
    data object Scan : AppRoute("scan")
    data object Settings : AppRoute("settings")
    data object Dan : AppRoute("dan")
    data object UsefulLinks : AppRoute("useful-links")
    data object SongDetail : AppRoute("song/{songIdentifier}") {
        fun create(songIdentifier: String): String = "song/${Uri.encode(songIdentifier)}"
    }
    data object DanDetail : AppRoute("dan/{categoryId}") {
        fun create(categoryId: String): String = "dan/${Uri.encode(categoryId)}"
    }
    data object B50 : AppRoute("b50")
    data object Recommendations : AppRoute("recommendations")
    data object PlateProgress : AppRoute("plate-progress")
    data object Scores : AppRoute("scores")
    data object Random : AppRoute("random")
    data object CommunityAliases : AppRoute("community-aliases")
    data object Profiles : AppRoute("profiles")
    data object StaticSync : AppRoute("static-sync")
    data object CloudAuth : AppRoute("cloud-auth")
    data object DataImport : AppRoute("data-import")
}
