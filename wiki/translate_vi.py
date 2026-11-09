from bs4 import BeautifulSoup, NavigableString
from pathlib import Path
import json, re, requests

ROOT = Path(__file__).resolve().parent
TEMPLATE = ROOT / "index.template.html"
DATA = ROOT / "i18n.json"
MARKER = "\n\uE000\n"
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


def chunks(items, limit=2200):
    current, size = [], 0
    for item in items:
        if current and size + len(item) + len(MARKER) > limit:
            yield current
            current, size = [], 0
        current.append(item)
        size += len(item) + len(MARKER)
    if current:
        yield current


def translate(batch):
    q = MARKER.join(batch)
    target = (
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=vi&dt=t&q="
        + requests.utils.quote(q, safe="")
    )
    r = requests.post("https://r.jina.ai/", json={"url": target}, timeout=90)
    r.raise_for_status()
    text = r.text.split("Markdown Content:", 1)[-1].strip()
    data = json.loads(text)
    joined = "".join(part[0] for part in data[0] if part and part[0])
    parts = joined.split(MARKER)
    if len(parts) != len(batch):
        raise ValueError(f"segments {len(parts)} != {len(batch)}")
    return [p.strip() for p in parts]


data = json.loads(DATA.read_text(encoding="utf-8"))
vi = data.get("vi", {})
for batch in chunks(all_texts):
    try:
        result = translate(batch)
    except Exception as error:
        print("batch failed", error)
        result = []
        for source in batch:
            try:
                result.extend(translate([source]))
            except Exception as e:
                print("string failed", source[:30], e)
                result.append(source)
    for source, translated in zip(batch, result):
        vi[source] = translated
    print("translated", len(vi), "/", len(all_texts))
data["vi"] = vi
DATA.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
print("done", len(data["vi"]), DATA.stat().st_size)
