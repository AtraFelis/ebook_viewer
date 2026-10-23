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
    val memberships: Map<String, Set<String>> = emptyMap()
) {
    init {
        val uris = records.map { it.uri }.toSet()
        val ids = shelves.map { it.id }.toSet()
        require(records.size <= 10_000 && uris.size == records.size)
        require(shelves.size <= 1_000 && ids.size == shelves.size)
        require(order.size == uris.size && order.toSet() == uris)
        require(memberships.keys.all { it in uris })
        require(memberships.values.all { it.isNotEmpty() && ids.containsAll(it) })
        require(memberships.values.sumOf { it.size.toLong() } <= 100_000)
    }

    fun visible(shelf: String?): List<ReadingRecord> {
        val byUri = records.associateBy { it.uri }
        return order.map { byUri.getValue(it) }.filter {
            shelf == null ||
                if (shelf.isEmpty()) {
                    it.uri !in memberships
                } else {
                    memberships[it.uri]?.contains(shelf) == true
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
        val updated = copy(
            records = (records + added).sortedByDescending { it.openedAt },
            order = order + added.map { it.uri }
        )
        // Importing an already-known URI into another shelf adds a relation, not a second record.
        return if (shelf == null) {
            updated
        } else {
            updated.addToShelves(values.map { it.uri }.toSet(), setOf(shelf))
        }
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
        if (shelf != null) selected.forEach { updated[it] = setOf(shelf) }
        return copy(memberships = updated)
    }

    fun addToShelves(uris: Set<String>, destinations: Set<String>): LibraryCatalog {
        require(shelves.map { it.id }.containsAll(destinations))
        val updated = memberships.toMutableMap()
        uris.intersect(order.toSet()).forEach { uri ->
            val ids = updated[uri].orEmpty() + destinations
            if (ids.isNotEmpty()) updated[uri] = ids
        }
        return copy(memberships = updated)
    }

    /** A move only replaces the source relation; other shelves and reading anchors survive. */
    fun transferToShelf(
        uris: Set<String>,
        source: String,
        target: String,
        keepSource: Boolean
    ): LibraryCatalog {
        require(shelves.any { it.id == target } && source != target)
        require(source.isEmpty() || shelves.any { it.id == source })
        val eligible = uris.intersect(visible(source).map { it.uri }.toSet())
        val base = if (keepSource || source.isEmpty()) this else removeFromShelf(eligible, source)
        return base.addToShelves(eligible, setOf(target))
    }

    fun reorderShelves(ids: List<String>): LibraryCatalog {
        require(ids.size == shelves.size && ids.toSet() == shelves.map { it.id }.toSet())
        val byId = shelves.associateBy { it.id }
        return copy(shelves = ids.map { byId.getValue(it) })
    }

    fun removeShelves(ids: Set<String>): LibraryCatalog = copy(
        shelves = shelves.filterNot { it.id in ids },
        memberships = memberships.mapValues { (_, value) ->
            value - ids
        }.filterValues { it.isNotEmpty() }
    )

    fun removeFromShelf(uris: Set<String>, shelf: String): LibraryCatalog = copy(
        memberships = memberships.mapValues { (uri, ids) ->
            if (uri in uris) ids - shelf else ids
        }.filterValues { it.isNotEmpty() }
    )

    fun moveShelfBy(id: String, direction: Int): LibraryCatalog {
        require(direction == -1 || direction == 1)
        val index = shelves.indexOfFirst { it.id == id }
        if (index < 0) return this
        val neighbor = shelves.getOrNull(index + direction) ?: return this
        return copy(
            shelves = shelves.toMutableList().apply {
                this[index + direction] = shelves[index]
                this[index] = neighbor
            }
        )
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
        memberships = memberships.mapValues { (_, ids) -> ids - id }
            .filterValues { it.isNotEmpty() }
    )
}
