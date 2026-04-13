package com.maestro.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Viewer : Screen("viewer/{pdfId}/{pageCount}/{uriEncoded}") {
        fun createRoute(pdfId: String, pageCount: Int, uriEncoded: String): String =
            "viewer/$pdfId/$pageCount/$uriEncoded"
    }
}
