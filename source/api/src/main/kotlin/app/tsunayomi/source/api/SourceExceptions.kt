package app.tsunayomi.source.api

/** The source needs setup by the user first, e.g. picking a folder or entering a server address. */
class SourceNotConfiguredException(message: String) : Exception(message)
