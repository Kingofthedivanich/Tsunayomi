package app.tsunayomi.source.api

/** Search filters a source exposes. The UI renders them generically by type. */
sealed class Filter<T>(val name: String, var state: T) {
    class Header(name: String) : Filter<Unit>(name, Unit)

    class Text(name: String, state: String = "") : Filter<String>(name, state)

    class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

    /** Include / exclude / ignore, as used for genre tags. */
    class TriState(name: String, state: Value = Value.Ignore) : Filter<TriState.Value>(name, state) {
        enum class Value { Ignore, Include, Exclude }
    }

    class Select(name: String, val values: List<String>, state: Int = 0) : Filter<Int>(name, state)

    class Sort(name: String, val values: List<String>, state: Selection? = null) :
        Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }

    class Group<F : Filter<*>>(name: String, filters: List<F>) : Filter<List<F>>(name, filters)
}

class FilterList(filters: List<Filter<*>> = emptyList()) : List<Filter<*>> by filters {
    constructor(vararg filters: Filter<*>) : this(filters.toList())
}

/** Names of the include/exclude and checkbox options inside filter groups, where sources keep their genres. */
fun List<Filter<*>>.tagOptions(): List<String> = flatMap { filter ->
    when (filter) {
        is Filter.Group<*> -> filter.state.filter { it is Filter.TriState || it is Filter.CheckBox }.map { it.name } +
            filter.state.filterIsInstance<Filter.Group<*>>().tagOptions()
        else -> emptyList()
    }
}.distinct()

/**
 * Switches on the filter option named [tag] (ignoring case): a genre checkbox, an include/exclude option or a value
 * of a select. Returns false when no option has that name.
 */
fun List<Filter<*>>.includeTag(tag: String): Boolean {
    for (filter in this) {
        when (filter) {
            is Filter.Group<*> -> if (filter.state.includeTag(tag)) return true
            is Filter.TriState -> if (filter.name.equals(tag, ignoreCase = true)) {
                filter.state = Filter.TriState.Value.Include
                return true
            }
            is Filter.CheckBox -> if (filter.name.equals(tag, ignoreCase = true)) {
                filter.state = true
                return true
            }
            is Filter.Select -> {
                // The first value of a select is usually "Any", never a tag.
                val index = filter.values.indexOfFirst { it.equals(tag, ignoreCase = true) }
                if (index > 0) {
                    filter.state = index
                    return true
                }
            }
            is Filter.Header, is Filter.Text, is Filter.Sort -> Unit
        }
    }
    return false
}
