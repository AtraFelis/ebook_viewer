package dev.example.ebookviewer

/** Main-thread session state; survives Activity recreation but never writes an install-wide flag. */
internal class ReadingHintState {
    private var offered = false
    var visible = false
        private set

    fun documentOpened() {
        if (!offered) {
            offered = true
            visible = true
        }
    }

    fun dismiss() {
        visible = false
    }
}
