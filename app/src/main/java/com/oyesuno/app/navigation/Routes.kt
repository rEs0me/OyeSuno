package com.oyesuno.app.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val TRIGGERS = "triggers"
    const val SETTINGS = "settings"
    const val RECORDER = "recorder"
    const val PLAYER = "player/{recordingId}"
    const val EDITOR = "editor/{recordingId}"

    fun playerRoute(recordingId: Long) = "player/$recordingId"
    fun editorRoute(recordingId: Long) = "editor/$recordingId"
}
