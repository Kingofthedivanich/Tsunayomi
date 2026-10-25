import os
import json
import re
import ssl
import sys
import time
import urllib.request

API_KEY = os.environ.get("GEMINI_API_KEY")
MODEL = os.environ.get("GEMINI_MODEL", "gemini-3.5-flash-lite")
if not API_KEY:
    print("Error: GEMINI_API_KEY environment variable is not set.", file=sys.stderr)
    print("Please run: export GEMINI_API_KEY='your-api-key'", file=sys.stderr)
    sys.exit(1)

URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={API_KEY}"

def translate_batch(batch, ctx):
    prompt = f"""You are an expert manga/manhwa/manhua translator.
Translate each line into natural, accurate Russian (ru) and English (en).
Keep the dialogue context, character tone, informal manga phrasing.
Return ONLY a valid JSON array of objects with keys: "id", "ru", "en".
Do not include any extra commentary.

Input lines:
{json.dumps([{"id": item["id"], "text": item["text"]} for item in batch], ensure_ascii=False, indent=2)}
"""
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    req = urllib.request.Request(URL, data=json.dumps(payload).encode("utf-8"), headers={"Content-Type": "application/json"})
    
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                text = data["candidates"][0]["content"]["parts"][0]["text"].strip()
                # Clean markdown code block if present
                clean_json = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.DOTALL).strip()
                parsed = json.loads(clean_json)
                return {x["id"]: (x["ru"], x["en"]) for x in parsed}
        except Exception as e:
            print(f"  Attempt {attempt + 1} failed: {e}", file=sys.stderr)
            time.sleep(2)
    raise RuntimeError(f"Failed to translate batch starting with {batch[0]['id']}")

def main():
    ctx = ssl._create_unverified_context()
    input_file = "tools/mt-eval/data/authentic_corpus.jsonl"
    output_file = "tools/mt-eval/data/gold_reference.jsonl"
    
    with open(input_file, "r", encoding="utf-8") as f:
        items = [json.loads(line) for line in f if line.strip()]
        
    print(f"Loaded {len(items)} items from {input_file}")
    
    batch_size = 15
    results = []
    
    for i in range(0, len(items), batch_size):
        batch = items[i:i + batch_size]
        print(f"Translating batch {i // batch_size + 1}/{(len(items) + batch_size - 1) // batch_size} ({batch[0]['id']} .. {batch[-1]['id']})...")
        translations = translate_batch(batch, ctx)
        for item in batch:
            ru, en = translations.get(item["id"], ("", ""))
            results.append({
                "id": item["id"],
                "lang": item["lang"],
                "text": item["text"],
                "gemini_ru": ru,
                "gemini_en": en,
            })
        time.sleep(1)
        
    with open(output_file, "w", encoding="utf-8") as f:
        for r in results:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            
    print(f"Successfully generated gold reference for {len(results)} items in {output_file}")

if __name__ == "__main__":
    main()
