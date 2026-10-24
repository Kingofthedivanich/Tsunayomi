# Отчет о проверке парсеров Kotatsu

Дата проверки: 2026-10-24T20:14:14.211337Z
Общее число кандидатов (с учетом фильтра `!isBroken` и языков `en`, `ru`, `ja`, `ko`, `zh`): **337**
Успешно прошли полную цепочку проверки: **66**
Не прошли проверку: **271**
Помечено как 18+ (NSFW): **30**
Время полного параллельного прогона (32 потока): **48 сек**

## Критерии прохождения
1. `getList(offset=0, filter=null)` возвращает непустой список тайтлов;
2. Из первых 3 тайтлов хотя бы у одного `getDetails(manga)` возвращает непустой список глав;
3. `getPages(chapter)` для последней главы возвращает список страниц;
4. Первая страница отдается по HTTP (код 200/206 при запросе первых 2 КБ) с валидной сигнатурой изображения (JPEG, PNG, WEBP, GIF);
5. Определение NSFW: по типу контента (`ADULT_TYPES`), тегам/рейтингу тайтлов и списку доменов.

## Топ причин отказов
| Причина отказа / Категория ошибки | Количество парсеров |
|---|---|
| `Catalog Parse Error` | 122 |
| `Empty Catalog List` | 58 |
| `HTTP 403 Forbidden` | 37 |
| `HTTP 404 Not Found` | 19 |
| `Connection / Read Timeout` | 12 |
| `No Chapters in First 3 Titles` | 9 |
| `Chapter Pages Parse Error` | 8 |
| `HTTP 5xx Server Error` | 2 |
| `Invalid Image Magic Bytes (HTML / Corrupted)` | 2 |
| `Image HTTP 400` | 1 |

## Прошедшие парсеры (Allowlist)
| Имя источника | Локаль | Домен | 18+ (NSFW) |
|---|---|---|---|
| `ANISASCANS` | `en` | `anisascans.in` | Нет |
| `ASMHENTAI` | `` | `asmhentai.com` | Да (18+) |
| `BANANASCAN_COM` | `en` | `bananascans.com` | Нет |
| `CLONEMANGA` | `en` | `manga.clone-army.org` | Нет |
| `DANKE` | `en` | `danke.moe` | Нет |
| `DEATHTOLLSCANS` | `en` | `reader.deathtollscans.net` | Нет |
| `DECADENCESCANS` | `en` | `reader.decadencescans.com` | Да (18+) |
| `DEMONICSCANS` | `en` | `demonicscans.org` | Нет |
| `ENTHUNDERSCANS` | `en` | `en-thunderscans.com` | Нет |
| `EXHENTAI` | `` | `e-hentai.org` | Да (18+) |
| `FLAMECOMICS` | `en` | `flamecomics.xyz` | Нет |
| `GDSCANS` | `en` | `gdscans.com` | Нет |
| `GEDECOMIX` | `en` | `gedecomix.com` | Да (18+) |
| `GRABBER` | `en` | `grabber.zone` | Нет |
| `GUYACUBARI` | `en` | `guya.cubari.moe` | Нет |
| `HACHIRUMI` | `en` | `hachirumi.com` | Да (18+) |
| `HENTAI20` | `en` | `hentai20.io` | Да (18+) |
| `HENTAI3ZCC` | `en` | `hentai3z.cc` | Да (18+) |
| `HENTAIERA` | `` | `hentaiera.com` | Да (18+) |
| `HENTAIFOX` | `` | `hentaifox.com` | Да (18+) |
| `HENTAIXCOMIC` | `en` | `hentaixcomic.com` | Да (18+) |
| `HENTAIXDICKGIRL` | `en` | `hentaixdickgirl.com` | Да (18+) |
| `HMANGABAT` | `en` | `www.mangabats.com` | Нет |
| `HOLOEARTH` | `` | `holoearth.com` | Нет |
| `IMHENTAI` | `` | `imhentai.xxx` | Да (18+) |
| `KIUTAKU` | `` | `kiutaku.com` | Да (18+) |
| `KLZ9` | `ja` | `klz9.com` | Да (18+) |
| `KSGROUPSCANS` | `en` | `ksgroupscans.com` | Нет |
| `LHTRANSLATION` | `en` | `lhtranslation.net` | Нет |
| `LIKEMANGA` | `en` | `likemanga.ink` | Нет |
| `MAHOUSHOUJOBU` | `` | `mahoushoujobu.com` | Нет |
| `MANGA18` | `en` | `manga18.club` | Да (18+) |
| `MANGA18FX` | `` | `manga18fx.com` | Да (18+) |
| `MANGABEE` | `en` | `mangazin.org` | Да (18+) |
| `MANGADASS` | `en` | `mangadass.com` | Да (18+) |
| `MANGADNA` | `en` | `mangadna.com` | Да (18+) |
| `MANGAECLIPSE` | `en` | `mangaeclipse.com` | Нет |
| `MANGAFORFREE` | `en` | `mangaforfree.com` | Да (18+) |
| `MANGAGEKO` | `en` | `www.mgeko.cc` | Нет |
| `MANGAGG` | `en` | `mangagg.com` | Нет |
| `MANGANATO` | `en` | `www.manganato.gg` | Нет |
| `MANGAOWL_IO` | `en` | `mangaowl.io` | Нет |
| `MANGAREAD` | `en` | `www.mangaread.org` | Нет |
| `MANGASUSHI` | `en` | `mangasushi.org` | Нет |
| `MANGATELLERS` | `en` | `reader.mangatellers.gr` | Нет |
| `MANHUAGUI` | `zh` | `www.manhuagui.com` | Нет |
| `MANHUAPLUS` | `en` | `manhuaplus.com` | Нет |
| `MANHUAPLUSORG` | `en` | `manhuaplus.org` | Нет |
| `MANHWA18CC` | `` | `manhwa18.cc` | Да (18+) |
| `MANHWA18COM` | `en` | `manhwa18.com` | Да (18+) |
| `MANHWADEN` | `en` | `www.manhwaden.com` | Да (18+) |
| `MANHWATOP` | `en` | `manhwatop.com` | Нет |
| `MISSKON` | `` | `misskon.com` | Да (18+) |
| `NHENTAI_XXX` | `` | `nhentai.xxx` | Да (18+) |
| `OMEGASCANS` | `en` | `omegascans.org` | Да (18+) |
| `PETROTECHSOCIETY` | `en` | `www.petrotechsociety.org` | Да (18+) |
| `PORNCOMIC18` | `en` | `18porncomic.com` | Да (18+) |
| `RACKUSREADS` | `en` | `rackusreads.com` | Нет |
| `RAW1001` | `ja` | `raw1001.net` | Нет |
| `SEINAGI` | `en` | `reader.seinagi.org.es` | Нет |
| `SHOJOSCANS` | `en` | `violetscans.com` | Нет |
| `TOONIZY` | `en` | `toonizy.com` | Да (18+) |
| `TOPMANHUA` | `en` | `manhuatop.org` | Нет |
| `TRITINIA` | `en` | `tritinia.org` | Нет |
| `XIUTAKU` | `zh` | `xiutaku.com` | Да (18+) |
| `YKMH` | `zh` | `www.ykmh.net` | Нет |
