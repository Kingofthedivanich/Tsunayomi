import json
import re

def clean_and_select(filename, out_filename, per_lang=50):
    seen = set()
    by_lang = {"ja": [], "ko": [], "zh": []}
    
    with open(filename, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            item = json.loads(line)
            lang = item["lang"]
            text = item["text"].strip()
            
            # Filter noise
            if len(text) < 2 or len(text) > 200:
                continue
            if re.fullmatch(r"[\d\s\W_]+", text):
                continue
            if text in seen:
                continue
                
            if lang == "ja":
                # Valid Japanese check
                if any("\u3040" <= c <= "\u30ff" or "\u4e00" <= c <= "\u9fff" for c in text):
                    # Exclude title or romanized noise
                    if not text.startswith("TSUMA") and not text.startswith("http"):
                        seen.add(text)
                        by_lang["ja"].append(text)
            elif lang == "ko":
                # Valid Korean check
                if any("\uac00" <= c <= "\ud7af" or "\u1100" <= c <= "\u11ff" for c in text):
                    seen.add(text)
                    by_lang["ko"].append(text)
            elif lang == "zh":
                # Valid Chinese check
                if any("\u4e00" <= c <= "\u9fff" for c in text):
                    # Filter pure English credits or watermark lines
                    if not text.startswith("TSUMUG") and not text.startswith("dasare") and not text.startswith("-["):
                        seen.add(text)
                        by_lang["zh"].append(text)

    records = []
    for lang in ["ja", "ko", "zh"]:
        selected = by_lang[lang][:per_lang]
        print(f"{lang}: selected {len(selected)} authentic lines")
        for i, text in enumerate(selected, 1):
            records.append({
                "id": f"{lang}_{i:03d}",
                "lang": lang,
                "text": text
            })
            
    with open(out_filename, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            
    print(f"Total corpus saved: {len(records)} items to {out_filename}")

if __name__ == "__main__":
    clean_and_select("tools/mt-eval/data/ocr-dump.jsonl", "tools/mt-eval/data/authentic_corpus.jsonl", per_lang=50)
