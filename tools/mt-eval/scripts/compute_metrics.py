#!/usr/bin/env python3
"""
Computes evaluation metrics across MT candidates (ML Kit, Opus-MT, NLLB-200)
against Silver Reference (Gemini 3.5 Flash-lite):
- chrF++ score (character n-gram F-score with word n-grams)
- Per-line chrF++ comparison against ML Kit on 50 random lines (automatic, no human judgement)
- Saves the per-line log to tools/mt-eval/data/pairwise_chrf.jsonl
- 20-sample user review table (HTML and Markdown)
- Performance and resource comparison (Mac CPU vs Emulator ML Kit)
- Threshold verification and REPORT.md generation with explicit data lineage
"""

import sys
import json
import random
from pathlib import Path
import sacrebleu

DATA_DIR = Path("tools/mt-eval/data")
REPORT_PATH = Path("tools/mt-eval/REPORT.md")
PAIRWISE_PATH = DATA_DIR / "pairwise_chrf.jsonl"
# Per-line chrF++ is noisy on short lines, so differences up to this many points count as a tie.
TIE_MARGIN = 2.0

def load_json(filename):
    p = DATA_DIR / filename
    if not p.exists():
        return None
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)

def calc_chrf_score(hypotheses, references):
    """Calculates chrF++ score (beta=2, char_order=6, word_order=2)"""
    if not hypotheses or not references:
        return 0.0
    res = sacrebleu.corpus_chrf(hypotheses, [references], word_order=2)
    return round(res.score, 2)

def calc_sentence_chrf(hypothesis, reference):
    """Calculates sentence-level chrF++ score"""
    if not hypothesis or not reference:
        return 0.0
    res = sacrebleu.sentence_chrf(hypothesis, [reference], word_order=2)
    return round(res.score, 2)

def generate_user_review_html(review_items, out_path):
    rows_html = ""
    for idx, item in enumerate(review_items, 1):
        bg = "#ffffff" if idx % 2 == 0 else "#f9fbfd"
        rows_html += f"""
        <tr style="background-color: {bg}; border-bottom: 1px solid #e2e8f0;">
            <td style="padding: 10px; font-weight: bold; text-align: center;">{idx}</td>
            <td style="padding: 10px; font-size: 0.9em; color: #4a5568;">
                <span style="display:inline-block; padding: 2px 6px; border-radius: 4px; background: #edf2f7; font-weight: bold; margin-bottom: 4px;">{item['lang'].upper()} ({item['source_manga']})</span><br/>
                <code>{item['source_text']}</code>
            </td>
            <td style="padding: 10px; font-size: 0.9em; background: #f0fff4;">
                <b>EN:</b> {item['silver_en']}<br/>
                <b>RU:</b> {item['silver_ru']}
            </td>
            <td style="padding: 10px; font-size: 0.9em; background: #ebf8ff;">
                <b>EN:</b> {item['mlkit_en']}<br/>
                <b>RU:</b> {item['mlkit_ru']}
            </td>
            <td style="padding: 10px; font-size: 0.9em;">
                <b>EN:</b> {item['opus_en']}<br/>
                <b>RU:</b> {item['opus_ru']}
            </td>
            <td style="padding: 10px; font-size: 0.9em;">
                <b>EN:</b> {item['nllb_en']}<br/>
                <b>RU:</b> {item['nllb_ru']}
            </td>
        </tr>
        """
    html_content = f"""<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <title>Tsunayomi MT Evaluation - 20 Sample Review</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 30px; background: #f7fafc; color: #2d3748; }}
        h1 {{ color: #1a202c; }}
        table {{ border-collapse: collapse; width: 100%; background: #ffffff; box-shadow: 0 4px 6px rgba(0,0,0,0.05); border-radius: 8px; overflow: hidden; }}
        th {{ background: #2b6cb0; color: white; padding: 12px 10px; text-align: left; font-size: 0.95em; }}
    </style>
</head>
<body>
    <h1>Сравнение качества машинного перевода (20 примеров манги)</h1>
    <p>Аутентичная выборка из 20 реплик манги/манхвы/маньхуа для визуальной оценки качества перевода на английский и русский языки.</p>
    <table>
        <thead>
            <tr>
                <th style="width: 40px;">#</th>
                <th style="width: 20%;">Исходный OCR-текст</th>
                <th style="width: 20%;">Серебряный эталон (Gemini 3.5 Flash-lite)</th>
                <th style="width: 20%;">ML Kit (Baseline)</th>
                <th style="width: 20%;">Opus-MT (Helsinki)</th>
                <th style="width: 20%;">NLLB-200 (600M)</th>
            </tr>
        </thead>
        <tbody>
            {rows_html}
        </tbody>
    </table>
</body>
</html>"""
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(html_content)

def main():
    print("Computing MT evaluation metrics...")
    dataset = load_json("eval_dataset.json")
    silver = load_json("silver_references.json")
    mlkit_data = load_json("mlkit_translations.json")
    opus_data = load_json("opus_translations.json")
    nllb_data = load_json("nllb_translations.json")

    if not dataset or not silver or not mlkit_data or not opus_data:
        print("Error: Missing required translation datasets.")
        sys.exit(1)

    # Index by id
    silver_map = {item["id"]: item for item in silver}
    mlkit_map = {item["id"]: item for item in mlkit_data["translations"]}
    opus_map = {item["id"]: item for item in opus_data["translations"]}
    nllb_map = {item["id"]: item for item in nllb_data["translations"]} if nllb_data else {}

    # Group IDs by language
    ja_ids = [d["id"] for d in dataset if d["lang"] == "ja"]
    ko_ids = [d["id"] for d in dataset if d["lang"] == "ko"]
    zh_ids = [d["id"] for d in dataset if d["lang"] == "zh"]
    all_ids = [d["id"] for d in dataset]

    def get_texts(ids, target, system):
        texts = []
        for i in ids:
            if system == "silver":
                texts.append(silver_map[i][f"silver_{target}"])
            elif system == "mlkit":
                texts.append(mlkit_map[i][f"mlkit_{target}"])
            elif system == "opus":
                texts.append(opus_map[i][f"opus_{target}"])
            elif system == "nllb":
                if i in nllb_map:
                    texts.append(nllb_map[i][f"nllb_{target}"])
                else:
                    texts.append("")
        return texts

    # Calculate corpus chrF++ scores
    results_chrf = {
        "mlkit": {},
        "opus": {},
        "nllb": {}
    }

    for lang_name, ids in [("ja", ja_ids), ("ko", ko_ids), ("zh", zh_ids), ("overall", all_ids)]:
        ref_en = get_texts(ids, "en", "silver")
        ref_ru = get_texts(ids, "ru", "silver")

        results_chrf["mlkit"][f"{lang_name}_en"] = calc_chrf_score(get_texts(ids, "en", "mlkit"), ref_en)
        results_chrf["mlkit"][f"{lang_name}_ru"] = calc_chrf_score(get_texts(ids, "ru", "mlkit"), ref_ru)

        results_chrf["opus"][f"{lang_name}_en"] = calc_chrf_score(get_texts(ids, "en", "opus"), ref_en)
        results_chrf["opus"][f"{lang_name}_ru"] = calc_chrf_score(get_texts(ids, "ru", "opus"), ref_ru)

        if nllb_map:
            results_chrf["nllb"][f"{lang_name}_en"] = calc_chrf_score(get_texts(ids, "en", "nllb"), ref_en)
            results_chrf["nllb"][f"{lang_name}_ru"] = calc_chrf_score(get_texts(ids, "ru", "nllb"), ref_ru)

    print("\n--- chrF++ Scores vs Silver Reference (Gemini 3.5 Flash-lite) ---")
    print(f"ML Kit:  Overall EN = {results_chrf['mlkit']['overall_en']}, Overall RU = {results_chrf['mlkit']['overall_ru']}")
    print(f"Opus-MT: Overall EN = {results_chrf['opus']['overall_en']}, Overall RU = {results_chrf['opus']['overall_ru']}")
    if nllb_map:
        print(f"NLLB-200: Overall EN = {results_chrf['nllb']['overall_en']}, Overall RU = {results_chrf['nllb']['overall_ru']}")

    # Per-line chrF++ comparison on 50 sampled rows (fixed seed for reproducibility).
    # This is not a human or blind evaluation: the outcome is decided by chrF++ alone.
    random.seed(42)
    sample_50_ids = random.sample(all_ids, 50)

    def outcome(diff):
        if diff > TIE_MARGIN:
            return "better"
        if diff < -TIE_MARGIN:
            return "worse"
        return "tie"

    pairwise_rows = []
    counts = {"opus": {"better": 0, "worse": 0, "tie": 0}, "nllb": {"better": 0, "worse": 0, "tie": 0}}

    for idx, sid in enumerate(sample_50_ids, 1):
        ref_en = silver_map[sid]["silver_en"]
        ref_ru = silver_map[sid]["silver_ru"]
        row = {
            "sample_num": idx,
            "id": sid,
            "lang": silver_map[sid]["lang"],
            "source_text": silver_map[sid]["source_text"],
            "silver_en": ref_en,
            "silver_ru": ref_ru,
        }
        scores = {}
        for system, mapping in [("mlkit", mlkit_map), ("opus", opus_map), ("nllb", nllb_map)]:
            if sid not in mapping:
                continue
            en = mapping[sid][f"{system}_en"]
            ru = mapping[sid][f"{system}_ru"]
            chrf_en = calc_sentence_chrf(en, ref_en)
            chrf_ru = calc_sentence_chrf(ru, ref_ru)
            scores[system] = round((chrf_en + chrf_ru) / 2.0, 2)
            row[system] = {"en": en, "ru": ru, "chrf_en": chrf_en, "chrf_ru": chrf_ru, "chrf_avg": scores[system]}
        for system in ("opus", "nllb"):
            if system in scores:
                diff = round(scores[system] - scores["mlkit"], 2)
                result = outcome(diff)
                counts[system][result] += 1
                row[f"{system}_vs_mlkit"] = {"chrf_diff": diff, "result": result}
        pairwise_rows.append(row)

    with open(PAIRWISE_PATH, "w", encoding="utf-8") as f:
        for row in pairwise_rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"Saved per-line chrF++ comparison to {PAIRWISE_PATH}")

    opus_wins, mlkit_wins_vs_opus, opus_ties = counts["opus"]["better"], counts["opus"]["worse"], counts["opus"]["tie"]
    nllb_wins, mlkit_wins_vs_nllb, nllb_ties = counts["nllb"]["better"], counts["nllb"]["worse"], counts["nllb"]["tie"]
    opus_win_rate = (opus_wins / 50.0) * 100.0
    nllb_win_rate = (nllb_wins / 50.0) * 100.0 if nllb_map else 0.0

    print(f"\n--- Per-line chrF++ vs ML Kit (50 samples, tie margin {TIE_MARGIN}) ---")
    print(f"Opus-MT:  better {opus_wins}, worse {mlkit_wins_vs_opus}, tie {opus_ties}")
    if nllb_map:
        print(f"NLLB-200: better {nllb_wins}, worse {mlkit_wins_vs_nllb}, tie {nllb_ties}")

    # Generate 20-sample user review set (balanced: 7 ja, 7 ko, 6 zh)
    user_review_20_ids = ja_ids[:7] + ko_ids[:7] + zh_ids[:6]
    user_review_items = []
    dataset_map = {d["id"]: d for d in dataset}
    for sid in user_review_20_ids:
        user_review_items.append({
            "id": sid,
            "lang": silver_map[sid]["lang"],
            "source_manga": dataset_map[sid]["source_manga"],
            "source_text": silver_map[sid]["source_text"],
            "silver_en": silver_map[sid]["silver_en"],
            "silver_ru": silver_map[sid]["silver_ru"],
            "mlkit_en": mlkit_map[sid]["mlkit_en"],
            "mlkit_ru": mlkit_map[sid]["mlkit_ru"],
            "opus_en": opus_map[sid]["opus_en"],
            "opus_ru": opus_map[sid]["opus_ru"],
            "nllb_en": nllb_map[sid]["nllb_en"] if sid in nllb_map else "—",
            "nllb_ru": nllb_map[sid]["nllb_ru"] if sid in nllb_map else "—"
        })

    html_out = DATA_DIR / "sample_review.html"
    generate_user_review_html(user_review_items, html_out)
    print(f"Saved 20-sample HTML review page to {html_out}")

    # Latencies
    lat_mlkit_bubble_en = mlkit_data["avg_bubble_latency_en_sec"] * 1000.0
    lat_mlkit_bubble_ru = mlkit_data["avg_bubble_latency_ru_sec"] * 1000.0
    lat_mlkit_page_en = mlkit_data["simulated_page_12_bubbles_en_sec"]
    lat_mlkit_page_ru = mlkit_data["simulated_page_12_bubbles_ru_sec"]

    lat_opus_bubble_en = opus_data["avg_bubble_latency_en_sec"] * 1000.0
    lat_opus_bubble_ru_step = opus_data["avg_bubble_latency_ru_sec"] * 1000.0
    lat_opus_bubble_ru_total = lat_opus_bubble_en + lat_opus_bubble_ru_step
    lat_opus_page_en = opus_data["simulated_page_12_bubbles_en_sec"]
    lat_opus_page_ru = opus_data["simulated_page_12_bubbles_ru_sec"]

    if nllb_data:
        lat_nllb_bubble_en = nllb_data["avg_bubble_latency_en_sec"] * 1000.0
        lat_nllb_bubble_ru = nllb_data["avg_bubble_latency_ru_sec"] * 1000.0
        lat_nllb_page_en = nllb_data["simulated_page_12_bubbles_en_sec"]
        lat_nllb_page_ru = nllb_data["simulated_page_12_bubbles_ru_sec"]
    else:
        lat_nllb_bubble_en = lat_nllb_bubble_ru = 0.0
        lat_nllb_page_en = lat_nllb_page_ru = 0.0

    def best(key):
        values = {name: results_chrf[system].get(key) for system, name in
                  [("mlkit", "ML Kit"), ("opus", "Opus-MT"), ("nllb", "NLLB-200")]}
        return max((v, n) for n, v in values.items() if v is not None)[1]

    def row(label, key):
        cells = []
        for system in ("mlkit", "opus", "nllb"):
            value = results_chrf[system].get(key, "—")
            name = {"mlkit": "ML Kit", "opus": "Opus-MT", "nllb": "NLLB-200"}[system]
            cells.append(f"**{value}**" if value != "—" and best(key) == name else str(value))
        return f"| {label} | " + " | ".join(cells) + " |"

    chrf_table = "\n".join([
        row("Японский → Английский", "ja_en"),
        row("Японский → Русский", "ja_ru"),
        row("Корейский → Английский", "ko_en"),
        row("Корейский → Русский", "ko_ru"),
        row("Китайский → Английский", "zh_en"),
        row("Китайский → Русский", "zh_ru"),
        row("Все языки → Английский", "overall_en"),
        row("Все языки → Русский", "overall_ru"),
    ])

    report_md = f"""# Сравнение офлайн-переводчиков: ML Kit, Opus-MT и NLLB-200

Дата: 2026-10-25

- ML Kit Translate запускался внутри приложения на эмуляторе Android (ARM64, Pixel 7). Код этого замера не закоммичен.
- Opus-MT и NLLB-200 запускались через Hugging Face Transformers на CPU Mac (Apple Silicon).
- Корпус: **150 строк** (50 JA, 50 KO, 50 ZH), снятых OCR приложения со страниц Nido Tensei (Klz9), 절대검감 (Naver Webtoon) и 被赶出了家门 (Manhuagui): `data/ocr-dump.jsonl` → `prepare_corpus.py` → `data/authentic_corpus.jsonl`.
- Эталон: перевод Gemini 3.5 Flash-lite (`gemini_translate.py` → `data/gold_reference.jsonl`, он же `data/silver_references.json`). Это машинный «серебряный» эталон, а не перевод человека.

**Ограничение корпуса.** Это отдельные строки OCR, а не целые пузыри: в корпус попали обрывки фраз и фуригана («たい», «おうと»). Приложение переводит пузырь целиком, поэтому абсолютные значения chrF++ здесь занижены у всех систем, а разница между системами говорит о качестве только приблизительно.

---

## 1. chrF++ относительно эталона

`sacrebleu`, chrF++ (beta=2, char_order=6, word_order=2). Жирным выделено лучшее значение в строке.

| Направление | ML Kit | Opus-MT | NLLB-200 (600M) |
|---|---|---|---|
{chrf_table}

Команда: `python3 tools/mt-eval/scripts/compute_metrics.py`.

> **Вывод по метрикам.** Все три системы примерно на одном уровне: общий разрыв не больше 2 баллов chrF++. ML Kit не лучше альтернатив: по общему баллу он последний и для английского, и для русского. Opus-MT заметно сильнее на корейском (+6 к EN, +4 к RU), ML Kit — на китайском → английский, NLLB — на японском → английский и китайском → русский. Явного победителя нет.

---

## 2. Построчное сравнение с ML Kit (50 случайных строк)

**Это не слепое и не ручное сравнение.** Переводы никто не читал и не оценивал. Для каждой строки считается chrF++ (среднее EN и RU) у каждой системы. Если разница с ML Kit больше {TIE_MARGIN} баллов, строка засчитывается как «лучше» или «хуже», иначе как ничья. Это та же метрика, что в разделе 1, только по отдельным строкам, поэтому самостоятельного подтверждения она не даёт.

| | Лучше ML Kit | Хуже ML Kit | Ничья |
|---|---|---|---|
| Opus-MT | {opus_wins} ({opus_win_rate:.0f}%) | {mlkit_wins_vs_opus} | {opus_ties} |
| NLLB-200 | {nllb_wins} ({nllb_win_rate:.0f}%) | {mlkit_wins_vs_nllb} | {nllb_ties} |

Протокол по строкам: `data/pairwise_chrf.jsonl`.

Критерий из ТЗ — «выигрыш у ML Kit в слепом сравнении не меньше чем в 2 случаях из 3» — этими данными не проверен: слепого сравнения не было. Чтобы проверить его честно, нужно, чтобы человек оценил пары переводов, не зная, какая система какая. Ни метрики, ни 20 примеров в `data/sample_review.html` не показывают преимущества, при котором такая проверка имела бы смысл.

---

## 3. Скорость, размер и лицензия

| Параметр | ML Kit | Opus-MT | NLLB-200 (600M) |
|---|---|---|---|
| Где измерялось | эмулятор Android (ARM64) | CPU Mac | CPU Mac |
| Размер моделей | ~30 МБ на языковой пакет | ~300 МБ × 4 пары ≈ 1.2 ГБ | 2.46 ГБ (FP32), ~600 МБ в INT8 |
| 1 строка → EN | {lat_mlkit_bubble_en:.1f} мс | {lat_opus_bubble_en:.1f} мс | {lat_nllb_bubble_en:.1f} мс |
| 1 строка → RU | {lat_mlkit_bubble_ru:.1f} мс | {lat_opus_bubble_ru_total:.1f} мс (пивот через EN: {lat_opus_bubble_en:.1f} + {lat_opus_bubble_ru_step:.1f}) | {lat_nllb_bubble_ru:.1f} мс |
| Страница, 12 строк → EN | {lat_mlkit_page_en:.2f} с | {lat_opus_page_en:.2f} с | {lat_nllb_page_en:.2f} с |
| Страница, 12 строк → RU | {lat_mlkit_page_ru:.2f} с | {lat_opus_page_ru:.2f} с | {lat_nllb_page_ru:.2f} с |
| Лицензия | бесплатный SDK Google | Apache 2.0 | CC-BY-NC 4.0 (некоммерческая) |

На телефоне Opus-MT и NLLB не запускались. Можно только ожидать, что на мобильном CPU они будут медленнее, чем на Mac; конкретных цифр для телефона нет. Уже на Mac перевод страницы на русский занимает {lat_opus_page_ru:.1f} с у Opus-MT и {lat_nllb_page_ru:.1f} с у NLLB — больше бюджета ТЗ в ~3 секунды.

---

## 4. Итог

**Opus-MT и NLLB-200 в приложение не встраиваем.** Причина не в качестве — по метрикам оно на уровне ML Kit, — а в том, что при сопоставимом качестве они:

1. весят в 40–80 раз больше ML Kit (1.2–2.5 ГБ против ~30 МБ на пару);
2. переводят страницу на русский дольше 3 секунд уже на Mac;
3. NLLB-200 нельзя распространять по некоммерческой лицензии CC-BY-NC 4.0.

ML Kit остаётся основным офлайн-переводчиком и запасным вариантом для онлайн-переводчиков. К вопросу можно вернуться, если появится компактная модель с заметно лучшим качеством; тогда понадобится корпус из целых пузырей и слепая ручная оценка.

---

## 5. Примеры

Таблица из 20 строк: [`data/sample_review.html`](data/sample_review.html).

### Первые 5 строк

"""
    for i, it in enumerate(user_review_items[:5], 1):
        report_md += f"""#### #{i} [{it['lang'].upper()}] {it['source_manga']}
- **OCR:** `{it['source_text']}`
- **Эталон (Gemini 3.5 Flash-lite):** EN: "{it['silver_en']}" | RU: "{it['silver_ru']}"
- **ML Kit:** EN: "{it['mlkit_en']}" | RU: "{it['mlkit_ru']}"
- **Opus-MT:** EN: "{it['opus_en']}" | RU: "{it['opus_ru']}"
- **NLLB-200:** EN: "{it['nllb_en']}" | RU: "{it['nllb_ru']}"

"""

    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        f.write(report_md)

    print(f"\nGenerated report: {REPORT_PATH}")

if __name__ == "__main__":
    main()
