#!/usr/bin/env python3
"""Custom HTML dashboard for the TMS Driver app E2E run.

Reads the Gradle JUnit XML (the authoritative list of tests, status and time, one folder per Gradle test
task) and enriches each test with the Allure result written by allure-junit5 (tags, Given/When/Then steps,
failure screenshots). Produces ONE self-contained HTML file plus a small summary.json. Standard library only.

    python3 scripts/generate_report.py                       # build/ -> build/reports/dashboard/index.html
    python3 scripts/generate_report.py --redact              # variant that is safe to publish (see below)

Two variants of the same report:
  * default  - failure screenshots are embedded and error text is complete. Screenshots can show the typed
               phone number, so keep this variant private (CI uploads it as a workflow artifact only).
  * --redact - no screenshots, error text cut to its first line, QA phone number masked. CI publishes this one.

Exit status is 0 even when tests failed (the report is a side artifact); use --fail-on-failures to change that.
"""
from __future__ import annotations

import argparse
import base64
import html
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

AREAS = {
    "AUTH": "Login screen",
    "OTP": "OTP verification",
    "PASS": "Passcode",
    "SES": "Session and sign out",
    "SH": "Home shell",
    "PR": "Profile",
    "CP": "Change passcode",
    "TRIP": "Trip lifecycle",
}
STATUS_ORDER = {"failed": 0, "skipped": 1, "passed": 2}
MAX_EMBED_BYTES = 12 * 1024 * 1024  # keep the single HTML file comfortably small


@dataclass
class Step:
    name: str
    status: str
    ms: int
    children: list["Step"] = field(default_factory=list)


@dataclass
class Case:
    suite: str            # Gradle task that ran it (smokeTest, authTest, ...)
    cls: str
    name: str
    status: str           # passed | failed | skipped
    seconds: float
    message: str = ""
    trace: str = ""
    tags: list[str] = field(default_factory=list)
    steps: list[Step] = field(default_factory=list)
    shots: list[Path] = field(default_factory=list)
    other_attachments: list[str] = field(default_factory=list)
    attempts: int = 1     # >1 when @RetryingTest ran the test again

    @property
    def retried(self) -> bool:
        return self.attempts > 1

    @property
    def case_id(self) -> str:
        m = re.search(r"\b([A-Z]{2,5})-\d{3}[a-z]?\b", self.name)
        return m.group(0) if m else ""

    @property
    def area(self) -> str:
        m = re.search(r"\b([A-Z]{2,5})-\d{3}", self.name)
        if m and m.group(1) in AREAS:
            return AREAS[m.group(1)]
        return "Other checks"

    @property
    def title(self) -> str:
        t = re.sub(r"^\s*\[[^\]]*\]\s*", "", self.name).replace("()", "").strip()
        return t or self.name


# ----------------------------------------------------------------------------- parsing

def clean_name(n: str) -> str:
    return n[:-2] if n.endswith("()") else n


def read_junit(results_dir: Path) -> list[Case]:
    cases: list[Case] = []
    for xml_file in sorted(results_dir.glob("*/TEST-*.xml")):
        suite_task = xml_file.parent.name
        for tc in ET.parse(xml_file).getroot().iter("testcase"):
            status, message, trace = "passed", "", ""
            fail = tc.find("failure")
            if fail is None:
                fail = tc.find("error")
            if fail is not None:
                status = "failed"
                message = fail.get("message") or ""
                trace = fail.text or ""
            elif tc.find("skipped") is not None:
                status = "skipped"
                message = tc.find("skipped").get("message") or ""
            cases.append(Case(
                suite=suite_task,
                cls=tc.get("classname", ""),
                name=clean_name(tc.get("name", "")),
                status=status,
                seconds=float(tc.get("time") or 0),
                message=message,
                trace=trace,
            ))
    return merge_attempts(cases)


def merge_attempts(cases: list[Case]) -> list[Case]:
    """@RetryingTest reports every earlier failed attempt as a skipped entry with the same name, followed by
    the final result. Collapse them into one case: the last entry decides the status, earlier ones count as
    attempts (so a test that passed on the second try is 'passed, retried', not passed plus skipped)."""
    merged: dict[tuple[str, str, str], Case] = {}
    order: list[tuple[str, str, str]] = []
    for c in cases:
        key = (c.suite, c.cls, c.name)
        if key not in merged:
            merged[key] = c
            order.append(key)
            continue
        first = merged[key]
        c.attempts = first.attempts + 1
        c.seconds += first.seconds
        if first.status == "failed" and not c.message:
            c.message = first.message
        merged[key] = c
    return [merged[k] for k in order]


def parse_steps(raw: list[dict]) -> list[Step]:
    return [Step(s.get("name", ""), s.get("status", "passed"), int(s.get("stop", 0)) - int(s.get("start", 0)),
                 parse_steps(s.get("steps", []))) for s in raw or []]


def enrich_from_allure(cases: list[Case], allure_dir: Path) -> None:
    """Attach tags, steps and attachments. If a test appears more than once, the newest result wins."""
    if not allure_dir.is_dir():
        return
    latest: dict[tuple[str, str], dict] = {}
    for f in allure_dir.glob("*-result.json"):
        try:
            r = json.loads(f.read_text())
        except (OSError, ValueError):
            continue
        labels = {}
        for lab in r.get("labels", []):
            labels.setdefault(lab["name"], []).append(lab["value"])
        key = ((labels.get("testClass") or [""])[0], clean_name(r.get("name", "")))
        if key not in latest or r.get("start", 0) >= latest[key].get("start", 0):
            r["_tags"] = labels.get("tag", [])
            latest[key] = r
    for c in cases:
        r = latest.get((c.cls, c.name))
        if not r:
            continue
        c.tags = sorted(set(r["_tags"]) - {"device"})
        c.steps = parse_steps(r.get("steps", []))
        atts = list(r.get("attachments", []))

        def collect(steps: list[dict]) -> None:
            for s in steps:
                atts.extend(s.get("attachments", []))
                collect(s.get("steps", []))
        collect(r.get("steps", []))
        for a in atts:
            src = allure_dir / a.get("source", "")
            if not src.is_file():
                continue
            if a.get("type") == "image/png":
                c.shots.append(src)
            else:
                c.other_attachments.append(a.get("name", src.name))


# ----------------------------------------------------------------------------- rendering helpers

def fmt_dur(seconds: float) -> str:
    if seconds < 1:
        return f"{int(seconds * 1000)} ms"
    if seconds < 60:
        return f"{seconds:.1f} s"
    m, s = divmod(int(round(seconds)), 60)
    return f"{m}m {s:02d}s"


def build_masker(redact: bool):
    secrets = []
    phone = re.sub(r"\D", "", os.environ.get("TMS_DRIVER_PHONE", ""))
    if redact and len(phone) >= 8:
        national = phone[-10:]
        secrets.append(re.compile(r"\D?".join(national)))
    def mask(text: str) -> str:
        for rx in secrets:
            text = rx.sub("[phone]", text)
        return text
    return mask


def render_steps(steps: list[Step], esc, mask, depth: int = 0) -> str:
    if not steps:
        return ""
    out = ["<ul class='steps'>"]
    for s in steps:
        icon = {"passed": "✓", "failed": "✗", "broken": "✗", "skipped": "–"}.get(s.status, "•")
        cls = "bad" if s.status in ("failed", "broken") else ("skip" if s.status == "skipped" else "ok")
        out.append(f"<li class='{cls}'><span class='ico'>{icon}</span>{esc(mask(s.name))}"
                   f"<span class='ms'>{fmt_dur(s.ms / 1000)}</span>")
        out.append(render_steps(s.children, esc, mask, depth + 1))
        out.append("</li>")
    out.append("</ul>")
    return "".join(out)


def render(cases: list[Case], meta: dict, args) -> tuple[str, dict]:
    esc = lambda s: html.escape(str(s), quote=True)
    mask = build_masker(args.redact)

    total = len(cases)
    passed = sum(c.status == "passed" for c in cases)
    failed = sum(c.status == "failed" for c in cases)
    skipped = sum(c.status == "skipped" for c in cases)
    retried = sum(c.retried for c in cases)
    flaky = sum(c.retried and c.status == "passed" for c in cases)
    executed = passed + failed
    rate = round(100 * passed / executed) if executed else 0
    duration = sum(c.seconds for c in cases)
    verdict = "PASSED" if failed == 0 and total > 0 else ("NO TESTS" if total == 0 else "FAILED")

    by_suite: dict[str, list[Case]] = {}
    by_area: dict[str, list[Case]] = {}
    for c in cases:
        by_suite.setdefault(c.suite, []).append(c)
        by_area.setdefault(c.area, []).append(c)

    def stats_rows(groups: dict[str, list[Case]]) -> str:
        rows = []
        for name, cs in sorted(groups.items()):
            p = sum(c.status == "passed" for c in cs)
            f = sum(c.status == "failed" for c in cs)
            s = sum(c.status == "skipped" for c in cs)
            rows.append(f"<tr><td>{esc(name)}</td><td>{len(cs)}</td><td class='g'>{p}</td>"
                        f"<td class='r'>{f}</td><td class='y'>{s}</td>"
                        f"<td>{fmt_dur(sum(c.seconds for c in cs))}</td></tr>")
        return "".join(rows)

    # screenshots (private variant only), bounded in total size
    embedded = 0
    def shot_html(c: Case) -> str:
        nonlocal embedded
        if args.redact or c.status != "failed":
            return ""
        parts = []
        for p in c.shots[:3]:
            size = p.stat().st_size
            if embedded + size > MAX_EMBED_BYTES:
                parts.append("<em>screenshot omitted (report size limit); see build/artifacts</em>")
                break
            embedded += size
            b64 = base64.b64encode(p.read_bytes()).decode()
            parts.append(f"<img class='shot' loading='lazy' alt='failure screenshot' src='data:image/png;base64,{b64}'>")
        return f"<div class='shots'>{''.join(parts)}</div>" if parts else ""

    def error_html(c: Case) -> str:
        if c.status == "skipped":
            return f"<div class='note'>Skipped: {esc(mask(c.message or 'no reason given'))}</div>" if c.message else ""
        if c.status != "failed":
            return ""
        first = mask((c.message or c.trace).strip().splitlines()[0] if (c.message or c.trace).strip() else "failed")
        if args.redact:
            return f"<div class='err'>{esc(first[:300])}</div>"
        body = mask((c.message or "").strip())
        trace = mask(c.trace.strip())
        return (f"<div class='err'>{esc(body[:2000])}</div>"
                f"<details><summary>Stack trace</summary><pre>{esc(trace[:6000])}</pre></details>")

    # failures first, then the rest grouped by area
    def row(c: Case, idx: int) -> str:
        search = f"{c.case_id} {c.title} {c.area} {c.suite} {' '.join(c.tags)} {c.status}{' retried' if c.retried else ''}".lower()
        icon = {"passed": "✅", "failed": "❌", "skipped": "⏭️"}[c.status]
        tags = "".join(f"<span class='tag'>{esc(t)}</span>" for t in c.tags)
        if c.retried:
            tags += f"<span class='tag retry'>retried: {c.attempts} attempts</span>"
        steps = render_steps(c.steps, esc, mask)
        other = ""
        if c.other_attachments and c.status == "failed":
            other = ("<div class='note'>Also captured (in the workflow artifact): "
                     + ", ".join(esc(n) for n in c.other_attachments) + "</div>")
        detail = f"{error_html(c)}{shot_html(c)}{steps}{other}"
        return (f"<tr class='t {c.status}' data-status='{c.status}' data-search='{esc(search)}' data-i='{idx}'>"
                f"<td>{icon}</td><td class='id'>{esc(c.case_id)}</td>"
                f"<td class='title'>{esc(mask(c.title))}<div class='tags'>{tags}</div></td>"
                f"<td>{esc(c.suite)}</td><td>{fmt_dur(c.seconds)}</td></tr>"
                f"<tr class='d' data-for='{idx}' hidden><td></td><td colspan='4'>{detail or '<em>No further detail.</em>'}</td></tr>")

    body_rows = []
    idx = 0
    for area in sorted(by_area, key=lambda a: (a == "Other checks", a)):
        cs = sorted(by_area[area], key=lambda c: (STATUS_ORDER[c.status], c.case_id, c.title))
        p = sum(c.status == "passed" for c in cs)
        body_rows.append(f"<tr class='area' data-area='{esc(area)}'><td colspan='5'><strong>{esc(area)}</strong>"
                         f"<span class='as'>{p}/{len(cs)} passed · {fmt_dur(sum(c.seconds for c in cs))}</span></td></tr>")
        for c in cs:
            body_rows.append(row(c, idx))
            idx += 1

    failures = [c for c in cases if c.status == "failed"]
    fail_block = ""
    if failures:
        items = "".join(
            f"<li><strong>{esc(c.case_id or c.cls.rsplit('.', 1)[-1])}</strong> {esc(mask(c.title))}"
            f"<div class='err'>{esc(mask(((c.message or c.trace).strip().splitlines() or ['failed'])[0])[:300])}</div></li>"
            for c in failures)
        fail_block = f"<section class='panel bad'><h2>Failures ({len(failures)})</h2><ul class='flist'>{items}</ul></section>"

    slow = sorted(cases, key=lambda c: -c.seconds)[:5]
    slow_rows = "".join(f"<tr><td>{esc(c.case_id)}</td><td>{esc(mask(c.title))}</td><td>{fmt_dur(c.seconds)}</td></tr>"
                        for c in slow)

    meta_bits = [(k, v) for k, v in meta.items() if v not in ("", None)]
    meta_html = " · ".join(f"<span><b>{esc(k)}</b> {esc(v)}</span>" for k, v in meta_bits)
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    colour = "#0d9e0d" if rate >= 90 else "#f9a825" if rate >= 70 else "#d93025"

    page = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(args.title)}</title>
<style>
*{{box-sizing:border-box;margin:0;padding:0}}
body{{font:14px -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f7fa;color:#333}}
.header{{background:linear-gradient(135deg,#1a73e8,#0d47a1);color:#fff;padding:28px 40px}}
.header h1{{font-size:24px;margin-bottom:6px}} .header .meta{{opacity:.9;font-size:13px;line-height:1.8}}
.header .meta span{{margin-right:6px}} .badge{{display:inline-block;margin-left:10px;padding:3px 12px;border-radius:14px;font-size:13px;
 background:{'#0d9e0d' if verdict == 'PASSED' else '#d93025'};vertical-align:middle}}
.cards{{display:flex;gap:16px;flex-wrap:wrap;padding:24px 40px 8px}}
.card{{background:#fff;border-radius:12px;padding:16px 22px;min-width:130px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.08);
 cursor:pointer;border:2px solid transparent}} .card.active{{border-color:#1a73e8}}
.card .n{{font-size:32px;font-weight:700}} .card .l{{font-size:11px;text-transform:uppercase;color:#666;margin-top:3px}}
.card.p .n{{color:#0d9e0d}} .card.f .n{{color:#d93025}} .card.s .n{{color:#f9a825}} .card.r .n{{color:{colour}}} .card.d .n{{font-size:22px;color:#5f6368}}
.bar{{height:8px;margin:14px 40px;border-radius:4px;overflow:hidden;display:flex;background:#e0e0e0}}
.bar i{{display:block;height:100%}}
.panel{{background:#fff;border-radius:12px;margin:16px 40px;padding:16px 22px;box-shadow:0 2px 8px rgba(0,0,0,.08)}}
.panel h2{{font-size:15px;margin-bottom:10px}} .panel.bad{{border-left:5px solid #d93025}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:0 24px;margin:0 40px}}
.grid .panel{{margin:16px 0}}
table{{border-collapse:collapse;width:100%}} th{{text-align:left;font-size:11px;text-transform:uppercase;color:#666;padding:8px 10px;border-bottom:2px solid #e0e0e0}}
td{{padding:8px 10px;border-bottom:1px solid #f0f0f0;vertical-align:top}} td.g{{color:#0d9e0d}} td.r{{color:#d93025}} td.y{{color:#b06000}}
.controls{{display:flex;gap:12px;flex-wrap:wrap;align-items:center;padding:8px 40px}}
.controls input{{flex:1 1 260px;padding:10px 14px;border:1px solid #d0d5dd;border-radius:8px;font-size:14px}}
.chip{{cursor:pointer;padding:7px 14px;border:1px solid #d0d5dd;border-radius:20px;background:#fff;font-weight:600;font-size:13px;user-select:none}}
.chip.active{{background:#1a73e8;border-color:#1a73e8;color:#fff}}
.tests{{margin:8px 40px 40px;background:#fff;border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,.08);overflow:hidden}}
tr.area td{{background:#f0f4ff;padding:9px 12px}} .as{{float:right;font-size:12px;color:#666}}
tr.t{{cursor:pointer}} tr.t:hover{{background:#fafbfc}} td.id{{font-family:monospace;font-size:12px;color:#555;white-space:nowrap}}
.title .tags{{margin-top:3px}} .tag.retry{{background:#fff3e0;color:#b45309;font-weight:600}}
.tag{{display:inline-block;font-size:10px;padding:1px 7px;margin-right:4px;border-radius:9px;background:#eef2f7;color:#456}}
tr.d td{{background:#fafbfc;padding:10px 14px 16px}}
.err{{font:12px monospace;color:#c5221f;background:#fce8e6;padding:7px 10px;border-radius:5px;margin:4px 0;white-space:pre-wrap;word-break:break-word}}
.note{{font-size:12px;color:#666;margin:6px 0}} pre{{font-size:11px;overflow:auto;max-height:260px;background:#f4f4f4;padding:8px;border-radius:5px;margin-top:6px}}
.shots{{display:flex;gap:12px;flex-wrap:wrap;margin:8px 0}} .shot{{max-height:420px;border:1px solid #ddd;border-radius:6px}}
ul.steps{{list-style:none;margin:6px 0 0 4px;padding-left:14px;border-left:2px solid #e6e9ef}}
ul.steps li{{padding:2px 0;font-size:13px}} .ico{{display:inline-block;width:16px;font-weight:700}}
li.ok>.ico{{color:#0d9e0d}} li.bad>.ico{{color:#d93025}} li.skip>.ico{{color:#b06000}} .ms{{margin-left:8px;color:#999;font-size:11px}}
.flist{{padding-left:18px}} .flist li{{margin-bottom:8px}}
.count{{padding:0 40px;color:#888;font-size:12px}} .foot{{text-align:center;color:#999;font-size:12px;padding:18px}}
@media(max-width:700px){{.header,.cards,.controls{{padding-left:16px;padding-right:16px}}.panel,.tests,.grid,.bar{{margin-left:16px;margin-right:16px}}}}
</style></head><body>
<div class="header"><h1>{esc(args.title)}<span class="badge">{verdict}</span></h1>
<div class="meta">{meta_html}<br><span><b>Generated</b> {generated}</span>{'<span><b>Variant</b> redacted (no screenshots)</span>' if args.redact else ''}</div></div>
<div class="cards">
<div class="card active" data-f="all"><div class="n">{total}</div><div class="l">Total</div></div>
<div class="card p" data-f="passed"><div class="n">{passed}</div><div class="l">Passed</div></div>
<div class="card f" data-f="failed"><div class="n">{failed}</div><div class="l">Failed</div></div>
<div class="card s" data-f="skipped"><div class="n">{skipped}</div><div class="l">Skipped</div></div>
{f'<div class="card s" data-f="retried"><div class="n">{retried}</div><div class="l">Retried</div></div>' if retried else ''}
<div class="card r" data-f="all"><div class="n">{rate}%</div><div class="l">Pass rate</div></div>
<div class="card d" data-f="all"><div class="n">{fmt_dur(duration)}</div><div class="l">Test time</div></div></div>
<div class="bar" title="passed / failed / skipped">
<i style="width:{100 * passed / total if total else 0}%;background:#0d9e0d"></i>
<i style="width:{100 * failed / total if total else 0}%;background:#d93025"></i>
<i style="width:{100 * skipped / total if total else 0}%;background:#f9a825"></i></div>
{fail_block}
<div class="grid">
<section class="panel"><h2>By suite (Gradle task)</h2><table><tr><th>Suite</th><th>Total</th><th>Pass</th><th>Fail</th><th>Skip</th><th>Time</th></tr>{stats_rows(by_suite)}</table></section>
<section class="panel"><h2>By area</h2><table><tr><th>Area</th><th>Total</th><th>Pass</th><th>Fail</th><th>Skip</th><th>Time</th></tr>{stats_rows(by_area)}</table></section>
<section class="panel"><h2>Slowest tests</h2><table><tr><th>ID</th><th>Test</th><th>Time</th></tr>{slow_rows}</table></section></div>
<div class="controls"><input type="search" id="q" placeholder="Search by ID, title, tag, suite…" aria-label="Search tests">
<span class="chip active" data-f="all">All</span><span class="chip" data-f="passed">Passed</span>
<span class="chip" data-f="failed">Failed</span><span class="chip" data-f="skipped">Skipped</span>{'<span class="chip" data-f="retried">Retried</span>' if retried else ''}</div>
<div class="count" id="count"></div>
<div class="tests"><table><thead><tr><th width="30"></th><th width="90">ID</th><th>Test (click to expand steps)</th><th width="110">Suite</th><th width="80">Time</th></tr></thead>
<tbody>{''.join(body_rows)}</tbody></table></div>
<div class="foot">TMS Driver app E2E · Appium + UiAutomator2 · JUnit 5</div>
<script>
(function(){{
 var f='all',q='',rows=[].slice.call(document.querySelectorAll('tr.t')),areas=[].slice.call(document.querySelectorAll('tr.area'));
 function apply(){{
  var shown=0,seen={{}};
  rows.forEach(function(r){{
   var ok=(f==='all'||r.dataset.status===f||(f==='retried'&&r.dataset.search.indexOf(' retried')>-1))&&(!q||r.dataset.search.indexOf(q)>-1);
   r.hidden=!ok; var d=document.querySelector('tr.d[data-for="'+r.dataset.i+'"]');
   if(!ok&&d)d.hidden=true; if(ok){{shown++;}}
  }});
  areas.forEach(function(a){{var n=a.nextElementSibling,any=false;
   while(n&&!n.classList.contains('area')){{if(n.classList.contains('t')&&!n.hidden)any=true;n=n.nextElementSibling;}}
   a.hidden=!any;}});
  document.getElementById('count').textContent='Showing '+shown+' of '+rows.length+' tests';
  [].forEach.call(document.querySelectorAll('.chip,.card[data-f]'),function(e){{
   e.classList.toggle('active',e.dataset.f===f&&!e.classList.contains('r')&&!e.classList.contains('d'));}});
 }}
 [].forEach.call(document.querySelectorAll('.chip,.card[data-f]'),function(e){{
  e.addEventListener('click',function(){{f=e.dataset.f;apply();}});}});
 document.getElementById('q').addEventListener('input',function(e){{q=e.target.value.toLowerCase().trim();apply();}});
 rows.forEach(function(r){{r.addEventListener('click',function(){{
  var d=document.querySelector('tr.d[data-for="'+r.dataset.i+'"]'); if(d)d.hidden=!d.hidden;}});
  if(r.dataset.status==='failed'){{var d=document.querySelector('tr.d[data-for="'+r.dataset.i+'"]'); if(d)d.hidden=false;}}}});
 apply();
}})();
</script></body></html>"""

    summary = {
        "verdict": verdict, "total": total, "passed": passed, "failed": failed, "skipped": skipped,
        "pass_rate": rate, "retried": retried, "passed_on_retry": flaky, "duration_seconds": round(duration, 1), "meta": meta,
        "suites": {k: {"total": len(v), "failed": sum(c.status == "failed" for c in v)} for k, v in by_suite.items()},
        "failures": [{"id": c.case_id, "title": mask(c.title), "suite": c.suite} for c in failures],
    }
    return page, summary


def write_markdown(summary: dict, path: Path, report_url: str) -> None:
    icon = "✅" if summary["verdict"] == "PASSED" else "❌"
    lines = [f"## {icon} Driver app E2E — {summary['verdict']}", "",
             "| Total | Passed | Failed | Skipped | Pass rate | Test time |", "|---|---|---|---|---|---|",
             f"| {summary['total']} | {summary['passed']} | {summary['failed']} | {summary['skipped']} | "
             f"{summary['pass_rate']}% | {fmt_dur(summary['duration_seconds'])} |", ""]
    if summary.get("retried"):
        lines += [f"⚠️ {summary['retried']} test(s) needed a retry ({summary['passed_on_retry']} passed on retry).", ""]
    if summary["failures"]:
        lines += ["**Failed tests**", ""] + [f"- `{f['id'] or f['suite']}` {f['title']}" for f in summary["failures"]] + [""]
    if report_url:
        lines.append(f"[Open the dashboard]({report_url})")
    with path.open("a") as fh:
        fh.write("\n".join(lines) + "\n")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--build-dir", default="build")
    ap.add_argument("--out-dir", default=None, help="default: <build-dir>/reports/dashboard")
    ap.add_argument("--title", default="TMS Driver App — E2E Test Dashboard")
    ap.add_argument("--meta", action="append", default=[], metavar="KEY=VALUE", help="shown in the header, repeatable")
    ap.add_argument("--meta-file", help="JSON object of extra header fields (e.g. APK package/version)")
    ap.add_argument("--redact", action="store_true", help="publishable variant: no screenshots, short errors, phone masked")
    ap.add_argument("--markdown", help="append a summary to this file (use $GITHUB_STEP_SUMMARY)")
    ap.add_argument("--report-url", default="", help="link added to the markdown summary")
    ap.add_argument("--github-output", help="append key=value lines (use $GITHUB_OUTPUT)")
    ap.add_argument("--fail-on-failures", action="store_true")
    args = ap.parse_args()

    build = Path(args.build_dir)
    out = Path(args.out_dir) if args.out_dir else build / "reports" / "dashboard"
    cases = read_junit(build / "test-results")
    enrich_from_allure(cases, build / "allure-results")
    if not cases:
        print(f"warning: no JUnit results found under {build / 'test-results'}", file=sys.stderr)

    meta: dict = {}
    if args.meta_file and Path(args.meta_file).is_file():
        meta.update(json.loads(Path(args.meta_file).read_text()))
    for kv in args.meta:
        k, _, v = kv.partition("=")
        meta[k.strip()] = v.strip()

    page, summary = render(cases, meta, args)
    out.mkdir(parents=True, exist_ok=True)
    (out / "index.html").write_text(page, encoding="utf-8")
    (out / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(f"dashboard: {out / 'index.html'}  ({summary['passed']}/{summary['total']} passed, {summary['failed']} failed)")

    if args.markdown:
        write_markdown(summary, Path(args.markdown), args.report_url)
    if args.github_output:
        with open(args.github_output, "a") as fh:
            for k in ("verdict", "total", "passed", "failed", "skipped", "pass_rate", "retried"):
                fh.write(f"{k}={summary[k]}\n")
            fh.write(f"duration={fmt_dur(summary['duration_seconds'])}\n")
    return 1 if (args.fail_on_failures and (summary["failed"] or not cases)) else 0


if __name__ == "__main__":
    sys.exit(main())
