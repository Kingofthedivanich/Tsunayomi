# Kotatsu Parsers Health Report

Generated at: 2026-10-24T17:47:24.035214Z

## Summary
- Total candidates tested: 14
- Passed: 5
- Failed: 9

## Verified Working Parsers (Added to Allowlist)
| Name | Locale | Domain | NSFW |
|---|---|---|---|
| `MANGACHAN` | `ru` | `manga-chan.me` | false |
| `LIKEMANGA` | `en` | `likemanga.ink` | false |
| `LHTRANSLATION` | `en` | `lhtranslation.net` | false |
| `TOONILY` | `en` | `toonily.com` | false |
| `KLZ9` | `ja` | `klz9.com` | true |

## Detailed Results
| Source | Status | Result / Error |
|---|---|---|
| `MANGACHAN` | PASS | Manga: 'Ванпанчмен', Chapters: 369, Pages: 24, Image: 676325 bytes (image/jpeg) |
| `READMANGA_RU` | FAIL | Exception: IOException: canceled due to java.lang.IllegalStateException: Unable to load PublicSuffixDatabase.list resource. |
| `SEIMANGA` | FAIL | Exception: ParseException: Cannot find "div.subject-meta, .cr-tags__wrapper a" at https://1.seimanga.me/v_imperatorskom_sadu_jivet_sobaka |
| `MINTMANGA` | FAIL | Exception: IOException: canceled due to java.lang.IllegalStateException: Unable to load PublicSuffixDatabase.list resource. |
| `SELFMANGA` | FAIL | getList returned empty list |
| `DESUME` | FAIL | Exception: JSONException: A JSONObject text must begin with '{' at 1 [character 2 line 1] |
| `COMX` | FAIL | Exception: NotFoundException: . Status=404, URL=[https://com-x.life/_c?t=MTQ2LjE5MC4xOS40OHwxNzkyODY1ODI3fDJ8MjAxMzMzfGVjYWQ1OWM1Zjc4NDIzYTA5MWFlMzEyOGQ2MGMzOWFl&u=https%3A%2F%2Fcom-x.life%2FComicList%2Fsort] |
| `LIKEMANGA` | PASS | Manga: 'The Painter Who Paints Dungeons', Chapters: 30, Pages: 12, Image: 5416922 bytes (image/jpeg) |
| `LHTRANSLATION` | PASS | Manga: 'Hole up Gamers', Chapters: 6, Pages: 67, Image: 1041370 bytes (image/jpeg) |
| `TOONILY` | PASS | Manga: 'The Father-In-Law Fucks Them All', Chapters: 64, Pages: 13, Image: 3942075 bytes (image/jpeg) |
| `KLZ9` | PASS | Manga: 'SALARYMAN, ISEKAI DE MESHI WO TABERU', Chapters: 5, Pages: 24, Image: 524704 bytes (image/png) |
| `RAWXZ` | FAIL | getList returned empty list |
| `WELOVEMANGA` | FAIL | getList returned empty list |
| `LEGIONSCANS` | FAIL | Exception: NotFoundException: . Status=404, URL=[https://legionscans.com/comics] |
