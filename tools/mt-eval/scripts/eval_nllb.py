#!/usr/bin/env python3
"""
Translates evaluation dataset using facebook/nllb-200-distilled-600M.
Direct pairs for Japanese (jpn_Jpan), Korean (kor_Hang), and Chinese (zho_Hans)
into English (eng_Latn) and Russian (rus_Cyrl).

Measures latency per line and page (12 bubbles).
Saves output to tools/mt-eval/data/nllb_translations.json.
"""

import os
import sys
import json
import time
import argparse
from pathlib import Path
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

MODEL_NAME = "facebook/nllb-200-distilled-600M"

LANG_MAP = {
    "ja": "jpn_Jpan",
    "ko": "kor_Hang",
    "zh": "zho_Hans",
    "en": "eng_Latn",
    "ru": "rus_Cyrl"
}

def translate(text, src_code, tgt_code, tokenizer, model, max_length=128):
    tokenizer.src_lang = src_code
    inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=max_length)
    tgt_id = tokenizer.convert_tokens_to_ids(tgt_code)
    with torch.no_grad():
        start = time.perf_counter()
        outputs = model.generate(
            **inputs,
            forced_bos_token_id=tgt_id,
            max_length=max_length
        )
        elapsed = time.perf_counter() - start
    decoded = tokenizer.decode(outputs[0], skip_special_tokens=True).strip()
    return decoded, elapsed

def main():
    parser = argparse.ArgumentParser(description="Evaluate NLLB-200 distilled 600M")
    parser.add_argument("--input", default="tools/mt-eval/data/eval_dataset.json", help="Input dataset path")
    parser.add_argument("--output", default="tools/mt-eval/data/nllb_translations.json", help="Output path")
    args = parser.parse_args()

    print(f"Loading tokenizer {MODEL_NAME}...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    print(f"Loading model {MODEL_NAME}...")
    model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_NAME, use_safetensors=True)
    model.eval()

    with open(args.input, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    results = []
    latencies_en = []
    latencies_ru = []

    print(f"Translating {len(dataset)} samples with NLLB-200...")
    for idx, item in enumerate(dataset, 1):
        lang = item["lang"]
        src_code = LANG_MAP[lang]
        source_text = item["ocr_text"]

        # Direct into EN
        trans_en, lat_en = translate(source_text, src_code, LANG_MAP["en"], tokenizer, model)
        latencies_en.append(lat_en)

        # Direct into RU
        trans_ru, lat_ru = translate(source_text, src_code, LANG_MAP["ru"], tokenizer, model)
        latencies_ru.append(lat_ru)

        results.append({
            "id": item["id"],
            "lang": lang,
            "source_text": source_text,
            "nllb_en": trans_en,
            "nllb_ru": trans_ru,
            "latency_en_sec": round(lat_en, 4),
            "latency_ru_sec": round(lat_ru, 4)
        })

        if idx % 10 == 0 or idx == len(dataset):
            print(f"  [{idx}/{len(dataset)} - {lang}] EN ({lat_en*1000:.0f}ms): {trans_en[:35]} | RU ({lat_ru*1000:.0f}ms): {trans_ru[:35]}")

    avg_en = (sum(latencies_en) / len(latencies_en)) if latencies_en else 0
    avg_ru = (sum(latencies_ru) / len(latencies_ru)) if latencies_ru else 0
    page_12_en = avg_en * 12
    page_12_ru = avg_ru * 12

    summary = {
        "model": MODEL_NAME,
        "sample_count": len(results),
        "avg_bubble_latency_en_sec": round(avg_en, 4),
        "avg_bubble_latency_ru_sec": round(avg_ru, 4),
        "simulated_page_12_bubbles_en_sec": round(page_12_en, 4),
        "simulated_page_12_bubbles_ru_sec": round(page_12_ru, 4),
        "translations": results
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(f"\nCompleted NLLB-200 evaluation!")
    print(f"Average latency per bubble (EN): {avg_en*1000:.1f} ms")
    print(f"Average latency per bubble (RU): {avg_ru*1000:.1f} ms")
    print(f"Page (12 bubbles) EN latency: {page_12_en:.2f} s")
    print(f"Page (12 bubbles) RU latency: {page_12_ru:.2f} s")
    print(f"Saved results to {output_path}")

if __name__ == "__main__":
    main()
