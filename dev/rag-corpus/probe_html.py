"""探测 HTML 文档页可提取正文量（临时脚本，不入库）"""
import subprocess, sys, tempfile, os, re
from html.parser import HTMLParser

CURL = r"C:\Windows\System32\curl.exe"


class TextExtractor(HTMLParser):
    SKIP = {"script", "style", "nav", "footer", "header", "aside", "noscript"}
    BLOCK = {"p", "div", "section", "article", "li", "h1", "h2", "h3", "h4", "h5", "pre", "td", "th"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.depth_skip = 0
        self.buf = []
        self.cur = []

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self.depth_skip += 1
        if tag in self.BLOCK and self.cur:
            self.buf.append("".join(self.cur).strip())
            self.cur = []

    def handle_endtag(self, tag):
        if tag in self.SKIP and self.depth_skip > 0:
            self.depth_skip -= 1
        if tag in self.BLOCK and self.cur:
            self.buf.append("".join(self.cur).strip())
            self.cur = []

    def handle_data(self, data):
        if self.depth_skip == 0:
            t = data.strip()
            if t:
                self.cur.append(t)

    def text(self):
        if self.cur:
            self.buf.append("".join(self.cur).strip())
        lines = [b for b in self.buf if len(b) > 1]
        return "\n".join(lines)


def fetch(url, out):
    r = subprocess.run([CURL, "-s", "-L", "-m", "25", "-A",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36",
                        "-o", out, "-w", "%{http_code}", url],
                       capture_output=True, text=True, timeout=40)
    return r.stdout.strip()


def analyze(url):
    tmp = os.path.join(tempfile.gettempdir(), "probe_" + str(abs(hash(url))) + ".html")
    code = fetch(url, tmp)
    if not os.path.exists(tmp):
        print(f"{code}  NOFILE  {url}")
        return
    raw = open(tmp, encoding="utf-8", errors="ignore").read()
    p = TextExtractor()
    p.feed(raw)
    text = p.text()
    cn = len(re.findall(r"[\u4e00-\u9fff]", text))
    print(f"{code}  html={len(raw):>7}  text={len(text):>7}  cn={cn:>6}  {url}")
    print("    head: " + text[:160].replace("\n", " / "))


if __name__ == "__main__":
    for u in [
        "https://kubernetes.io/zh-cn/docs/concepts/overview/",
        "https://kubernetes.io/zh-cn/docs/concepts/workloads/pods/",
        "https://element-plus.org/zh-CN/component/button.html",
        "https://cn.vitejs.dev/guide/why.html",
        "https://docs.spring.io/spring-boot/reference/index.html",
    ]:
        try:
            analyze(u)
        except Exception as e:
            print("ERR", u, repr(e))
