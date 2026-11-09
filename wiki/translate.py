from bs4 import BeautifulSoup, NavigableString
from pathlib import Path
import json, re, time, requests

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "index.template.html"
OUT = ROOT / "i18n.json"
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

soup = BeautifulSoup(HTML.read_text(encoding="utf-8"), "html.parser")
texts, attrs = set(EXTRA), set()
for node in soup.find_all(string=True):
    if not isinstance(node, NavigableString) or not node.parent:
        continue
    if node.parent.name in SKIP:
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
print("strings:", len(all_texts), "chars:", sum(len(s) for s in all_texts))


def translate_batch(batch, target):
    q = MARKER.join(batch)
    url = ("https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl="
           + target + "&dt=t&q=" + requests.utils.quote(q, safe=""))
    response = requests.post("https://r.jina.ai/", json={"url": url}, timeout=90)
    response.raise_for_status()
    text = response.text.split("Markdown Content:", 1)[-1].strip()
    data = json.loads(text)
    return "".join(part[0] for part in data[0] if part and part[0])


def chunks(items, limit=3200):
    current, size = [], 0
    for item in items:
        if current and size + len(item) + len(MARKER) > limit:
            yield current
            current, size = [], 0
        current.append(item)
        size += len(item) + len(MARKER)
    if current:
        yield current


def translate_all(target):
    mapping = {}
    for batch in chunks(all_texts):
        for attempt in range(4):
            try:
                translated = translate_batch(batch, target)
                parts = translated.split(MARKER)
                if len(parts) == len(batch):
                    for source, result in zip(batch, parts):
                        mapping[source] = re.sub(r"\s+", " ", result).strip()
                    break
                raise ValueError(f"segment mismatch {len(parts)} != {len(batch)}")
            except Exception as error:
                print(target, "retry", attempt + 1, error)
                time.sleep(1.5 * (attempt + 1))
        else:
            for source in batch:
                for attempt in range(3):
                    try:
                        mapping[source] = translate_batch([source], target).strip()
                        break
                    except Exception:
                        time.sleep(0.8)
                time.sleep(0.05)
        time.sleep(0.2)
    return mapping


result = {}
for target in TARGETS:
    print("translating", target)
    result[target] = translate_all(target)
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
    print("saved", target, len(result[target]))

print("done", OUT, OUT.stat().st_size)
