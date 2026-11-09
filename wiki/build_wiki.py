from pathlib import Path
import base64, json, re, shutil

ROOT = Path(__file__).resolve().parent
TEMPLATE = ROOT / "index.template.html"
DATA = ROOT / "i18n.json"
INDEX = ROOT / "index.html"
SINGLE = ROOT / "ModSync-Wiki.html"

template = TEMPLATE.read_text(encoding="utf-8")
i18n = json.loads(DATA.read_text(encoding="utf-8"))
payload = json.dumps(i18n, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
full = re.sub(
    r'<script id="i18n-data" type="application/json">.*?</script>',
    '<script id="i18n-data" type="application/json">' + payload + "</script>",
    template,
    flags=re.S,
)
INDEX.write_text(full, encoding="utf-8")

single = full
for rel in ("icon.png", "review.png", "result.png", "language.png"):
    data = base64.b64encode((ROOT / "images" / rel).read_bytes()).decode("ascii")
    single = single.replace(f'src="images/{rel}"', f'src="data:image/png;base64,{data}"')
SINGLE.write_text(single, encoding="utf-8")
print("index", INDEX.stat().st_size, "single", SINGLE.stat().st_size)
