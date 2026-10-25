#!/usr/bin/env python3
"""
Translates evaluation dataset using Helsinki-NLP Opus-MT models:
- opus-mt-ja-en
- opus-mt-ko-en
- opus-mt-zh-en
- opus-mt-en-ru (for pivot into Russian)

Measures latency per line and page (12 bubbles).
Saves output to tools/mt-eval/data/opus_translations.json.
"""

import os
import sys
import json
import time
import argparse
from pathlib import Path
import torch
from transformers import MarianMTModel, MarianTokenizer

MODELS = {
    "ja-en": "Helsinki-NLP/opus-mt-ja-en",
    "ko-en": "Helsinki-NLP/opus-mt-ko-en",
    "zh-en": "Helsinki-NLP/opus-mt-zh-en",
    "en-ru": "Helsinki-NLP/opus-mt-en-ru"
}

def load_pipeline(model_name):
    print(f"Loading {model_name}...")
    tokenizer = MarianTokenizer.from_pretrained(model_name)
    model = MarianMTModel.from_pretrained(model_name, use_safetensors=True)
    model.eval()
    return tokenizer, model

def translate(text, tokenizer, model, max_length=128):
    inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=max_length)
    with torch.no_grad():
        start = time.perf_counter()
        outputs = model.generate(**inputs, max_length=max_length)
        elapsed = time.perf_counter() - start
    decoded = tokenizer.decode(outputs[0], skip_special_tokens=True).strip()
    return decoded, elapsed

def main():
    parser = argparse.ArgumentParser(description="Evaluate Opus-MT models")
    parser.add_argument("--input", default="tools/mt-eval/data/eval_dataset.json", help="Input dataset path")
    parser.add_argument("--output", default="tools/mt-eval/data/opus_translations.json", help="Output path")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    # Group by language
    by_lang = {"ja": [], "ko": [], "zh": []}
    for item in dataset:
        by_lang[item["lang"]].append(item)

    results = []
    latencies_en = []
    latencies_ru = []

    # 1. Load en-ru pivot model first or on demand
    en_ru_tok, en_ru_model = load_pipeline(MODELS["en-ru"])

    for lang in ["ja", "ko", "zh"]:
        model_name = MODELS[f"{lang}-en"]
        tok, model = load_pipeline(model_name)
        items = by_lang[lang]
        print(f"Translating {len(items)} samples for {lang}...")
        
        for idx, item in enumerate(items, 1):
            source_text = item.get("source_text") or item.get("ocr_text")
            
            # Translate to English
            trans_en, lat_en = translate(source_text, tok, model)
            latencies_en.append(lat_en)
            
            # Pivot translate to Russian
            trans_ru, lat_ru = translate(trans_en, en_ru_tok, en_ru_model)
            latencies_ru.append(lat_ru)
            
            total_lat = lat_en + lat_ru
            results.append({
                "id": item["id"],
                "lang": lang,
                "source_text": source_text,
                "opus_en": trans_en,
                "opus_ru": trans_ru,
                "latency_en_sec": round(lat_en, 4),
                "latency_ru_sec": round(lat_ru, 4),
                "total_latency_sec": round(total_lat, 4)
            })
            if idx % 20 == 0 or idx == len(items):
                print(f"  [{lang} {idx}/{len(items)}] EN: {trans_en[:40]}... (EN: {lat_en*1000:.1f}ms, RU: {lat_ru*1000:.1f}ms)")

    avg_en = (sum(latencies_en) / len(latencies_en)) if latencies_en else 0
    avg_ru = (sum(latencies_ru) / len(latencies_ru)) if latencies_ru else 0
    page_12_en = avg_en * 12
    page_12_ru = (avg_en + avg_ru) * 12

    summary = {
        "model": "Helsinki-NLP/opus-mt",
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

    print(f"\nCompleted Opus-MT evaluation!")
    print(f"Average latency per bubble (EN): {avg_en*1000:.1f} ms")
    print(f"Average latency per bubble (RU pivot): {(avg_en+avg_ru)*1000:.1f} ms")
    print(f"Page (12 bubbles) EN latency: {page_12_en:.2f} s")
    print(f"Page (12 bubbles) RU latency: {page_12_ru:.2f} s")
    print(f"Saved results to {output_path}")

if __name__ == "__main__":
    main()
