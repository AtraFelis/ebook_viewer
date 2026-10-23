package dev.example.ebookviewer

/** Organization belongs to the catalog, so stale reading-position saves cannot undo a move. */
data class Bookshelf(val id: String, val name: String) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 100)
    }
}

data class LibraryCatalog(
    val records: List<ReadingRecord> = emptyList(),
    val shelves: List<Bookshelf> = emptyList(),
    val order: List<String> = records.map { it.uri },
    val memberships: Map<String, String> = emptyMap()
) {
    init {
        val uris = records.map { it.uri }.toSet()
        val ids = shelves.map { it.id }.toSet()
        require(records.size <= 10_000 && uris.size == records.size)
        require(shelves.size <= 1_000 && ids.size == shelves.size)
        require(order.size == uris.size && order.toSet() == uris)
        require(memberships.keys.all { it in uris } && memberships.values.all { it in ids })
    }

    fun visible(shelf: String?): List<ReadingRecord> {
        val byUri = records.associateBy { it.uri }
        return order.map { byUri.getValue(it) }.filter {
            shelf == null ||
                if (shelf.isEmpty()) {
                    it.uri !in memberships
                } else {
                    memberships[it.uri] == shelf
                }
        }
    }

    fun put(record: ReadingRecord, replacedUri: String? = null): LibraryCatalog {
        val replaced = replacedUri?.takeIf { it != record.uri && records.any { r -> r.uri == it } }
        val existing = records.any { it.uri == record.uri }
        val updatedOrder = order.mapNotNull {
            when {
                it == replaced && !existing -> record.uri
                it == replaced -> null
                else -> it
            }
        }.let { if (existing || replaced != null) it else it + record.uri }
        val updatedMemberships = memberships.toMutableMap()
        if (replaced != null) {
            val shelf = updatedMemberships.remove(replaced)
            if (!existing && shelf != null) updatedMemberships[record.uri] = shelf
        }
        return copy(
            records = (records.filter { it.uri != record.uri && it.uri != replaced } + record)
                .sortedByDescending { it.openedAt },
            order = updatedOrder,
            memberships = updatedMemberships
        )
    }

    /** Existing records retain their anchor, title, opened time and placement when selected again. */
    fun addAll(values: List<ReadingRecord>, shelf: String?): LibraryCatalog {
        require(shelf == null || shelves.any { it.id == shelf })
        val known = records.map { it.uri }.toMutableSet()
        val added = values.filter { known.add(it.uri) }
        return copy(
            records = (records + added).sortedByDescending { it.openedAt },
            order = order + added.map { it.uri },
            memberships = if (shelf == null) {
                memberships
            } else {
                memberships + added.associate { it.uri to shelf }
            }
        )
    }

    fun remove(uris: Set<String>): LibraryCatalog = copy(
        records = records.filterNot { it.uri in uris },
        order = order.filterNot { it in uris },
        memberships = memberships.filterKeys { it !in uris }
    )

    fun moveToShelf(uris: Set<String>, shelf: String?): LibraryCatalog {
        require(shelf == null || shelves.any { it.id == shelf })
        val selected = uris.intersect(order.toSet())
        val updated = memberships.filterKeys { it !in selected }.toMutableMap()
        if (shelf != null) selected.forEach { updated[it] = shelf }
        return copy(memberships = updated)
    }

    fun moveBy(uri: String, direction: Int, filter: String?): LibraryCatalog {
        require(direction == -1 || direction == 1)
        val visible = visible(filter).map { it.uri }
        val index = visible.indexOf(uri)
        if (index < 0) return this
        val neighbor = visible.getOrNull(index + direction) ?: return this
        val first = order.indexOf(uri)
        val second = order.indexOf(neighbor)
        return copy(
            order = order.toMutableList().apply {
                this[first] = neighbor
                this[second] = uri
            }
        )
    }

    fun addShelf(shelf: Bookshelf): LibraryCatalog {
        require(shelves.none { it.id == shelf.id })
        return copy(shelves = shelves + shelf)
    }

    fun renameShelf(id: String, name: String): LibraryCatalog {
        require(shelves.any { it.id == id })
        return copy(shelves = shelves.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun removeShelf(id: String): LibraryCatalog = copy(
        shelves = shelves.filterNot { it.id == id },
        memberships = memberships.filterValues { it != id }
    )
}
