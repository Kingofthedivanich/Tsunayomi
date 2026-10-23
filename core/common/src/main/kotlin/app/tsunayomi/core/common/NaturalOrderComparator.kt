package app.tsunayomi.core.common

/**
 * Compares strings the way people expect file names to sort: "page 2" before "page 10".
 * Digit runs are compared by numeric value, everything else case-insensitively.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val result = compareDigitRuns(a.substring(startA, i), b.substring(startB, j))
                if (result != 0) return result
            } else {
                val result = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (result != 0) return result
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j).takeIf { it != 0 } ?: a.compareTo(b)
    }

    private fun compareDigitRuns(a: String, b: String): Int {
        val trimmedA = a.trimStart('0')
        val trimmedB = b.trimStart('0')
        if (trimmedA.length != trimmedB.length) return trimmedA.length.compareTo(trimmedB.length)
        val byValue = trimmedA.compareTo(trimmedB)
        // Equal values: fewer leading zeros first, so "1" < "01" deterministically.
        return if (byValue != 0) byValue else a.length.compareTo(b.length)
    }
}
