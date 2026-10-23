package app.tsunayomi.source.api

/** The source needs setup by the user first, e.g. picking a folder or entering a server address. */
class SourceNotConfiguredException(message: String) : Exception(message)

/**
 * The title has chapters, but none can be read in the app — e.g. MangaDex lists only links
 * to the publisher's site for licensed series. The UI explains this instead of showing an empty list.
 */
class ChaptersOnlyExternalException(val externalCount: Int) :
    Exception("All $externalCount chapters are hosted on external sites")
