from bs4 import BeautifulSoup, NavigableString
from pathlib import Path
import json, re, time, requests

ROOT = Path(__file__).resolve().parent
TEMPLATE = ROOT / "index.template.html"
DATA = ROOT / "i18n.json"
MARKER = "\n\uE000\n"
TARGETS = ["en", "ja", "ko", "de", "fr", "es", "pt", "it", "ru", "tr", "pl", "nl", "th", "id", "vi"]
SKIP = {"script", "style", "pre", "code", "select", "option", "svg"}
EXTRA = [
    "ModSync Wiki · 联机模组同步使用指南",
    "没有找到。试试“安装”“端口”“报错”。",
    "复制",
    "已复制",
    "截图预览",
]

soup = BeautifulSoup(TEMPLATE.read_text(encoding="utf-8"), "html.parser")
texts, attrs = set(EXTRA), set()
for node in soup.find_all(string=True):
    if not isinstance(node, NavigableString) or not node.parent or node.parent.name in SKIP:
        continue
    value = re.sub(r"\s+", " ", str(node)).strip()
    if value and re.search(r"[A-Za-z\u4e00-\u9fff]", value):
        texts.add(value)
for el in soup.find_all(True):
    for name in ("placeholder", "title", "aria-label", "alt"):
        value = el.get(name)
        if value:
            value = re.sub(r"\s+", " ", value).strip()
            if value and re.search(r"[A-Za-z\u4e00-\u9fff]", value):
                attrs.add(value)
all_texts = sorted(texts | attrs, key=len)
data = json.loads(DATA.read_text(encoding="utf-8"))


def translate(values, code):
    q = MARKER.join(values)
    url = ("https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl="
           + code + "&dt=t&q=" + requests.utils.quote(q, safe=""))
    r = requests.post("https://r.jina.ai/", json={"url": url}, timeout=90)
    r.raise_for_status()
    text = r.text.split("Markdown Content:", 1)[-1].strip()
    parsed = json.loads(text)
    joined = "".join(part[0] for part in parsed[0] if part and part[0])
    parts = joined.split(MARKER)
    if len(parts) != len(values):
        raise ValueError(f"segments {len(parts)} != {len(values)}")
    return [p.strip() for p in parts]


for lang in TARGETS:
    table = data.setdefault(lang, {})
    missing = [text for text in all_texts if text not in table]
    if not missing:
        print(lang, "complete")
        continue
    for start in range(0, len(missing), 8):
        batch = missing[start:start + 8]
        for attempt in range(3):
            try:
                result = translate(batch, lang)
                break
            except Exception as error:
                print(lang, "retry", attempt + 1, error)
                time.sleep(2)
        else:
            result = batch
        for source, translated in zip(batch, result):
            table[source] = translated
        print(lang, "added", len(batch))
    time.sleep(1)
DATA.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
print("missing after", {lang: len([t for t in all_texts if t not in data[lang]]) for lang in TARGETS})
print("size", DATA.stat().st_size)
