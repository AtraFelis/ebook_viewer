package dev.example.ebookviewer

/** One worker batch owns at most one page. Never retain a native page across scheduler yields. */
class RenderPageSession<P : AutoCloseable>(private val open: (Int) -> P) : AutoCloseable {
    private var current: P? = null
    private var index = -1
    private var closed = false

    fun page(index: Int): P {
        check(!closed)
        if (this.index == index) current?.let { return it }
        release()
        val next = open(index)
        current = next
        this.index = index
        return next
    }

    fun release() {
        val previous = current
        current = null
        index = -1
        previous?.close()
    }

    override fun close() {
        if (closed) return
        closed = true
        release()
    }
}
