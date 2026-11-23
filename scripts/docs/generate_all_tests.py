#!/usr/bin/env python3
"""Collects every automated test from the Kotlin sources and writes docs/ALL_TESTS.md, docs/all-tests.csv and (when reportlab is installed)
docs/ALL_TESTS.pdf. Run from the repository root:  python3 scripts/docs/generate_all_tests.py

A test is a method annotated @Test or @RetryingTest in src/test/kotlin (exploration helpers in explore/ are not counted).
Its suite comes from its tags: @Tag("smoke") -> smoke + regression, @Tag("sms") -> manual only, @Disabled -> skipped, other
device tests -> regression only; tests under backend/, config/ and fixtures/ run without a device."""
import csv, datetime, glob, os, re, sys

BASE = 'src/test/kotlin/tms/driver'
NO_DEVICE_DIRS = ('backend/', 'config/', 'fixtures/')
GROUPS = {  # friendly names; any class not listed uses its own name
    'LaunchSmokeTest': 'Launch smoke', 'OdAsapDriverApp': 'Login, session, shell, profile, OTP',
    'TripLifecycleTest': 'T1 Home with a ride', 'HomeWithDispatchTest': 'T1 Home with a ride', 'StartTripTest': 'T2 Start trip and shuttle',
    'TripViewTest': 'T3 Trip view and arriving', 'ReachDestinationTest': 'T4 Reach destination', 'RequestsTabsTest': 'T5 Requests tabs and card actions',
    'PassengerActionsTest': 'T5 Rider sheet actions', 'RequestsIntakeTest': 'T6 Taking a ride from the queue', 'OperatorEventsTest': 'T7 Operator and rider events',
    'TwoRidesTest': 'T7 Two rides', 'StateLifecycleTest': 'T7 App lifecycle', 'MultiRiderStopTest': 'T7 Two riders at one stop', 'LocationTest': 'T8 Location',
    'ShuttleTest': 'T9 Shuttle', 'HelpTest': 'T10 Help Center', 'NavigationTest': 'T11 Navigation', 'MapAndDropoffTest': 'T12 Map tab and Change Drop-off',
    'OperatorOnTripViewTest': 'T12 Operator on the trip view', 'ProfilePhotoTest': 'T12 Profile photo', 'RequestsSearchTest': 'T14 Search on the Requests screen',
    'ConfigSanityTest': 'Configuration checks', 'DriverApiClientTest': 'Driver API client (stub server)', 'RiderApiClientTest': 'Rider API client and ride data (stub server)',
    'RideFixtureTest': 'Ride fixture and cleanup (stub servers)',
}
ORDER = list(GROUPS)  # known classes first, in this order; unknown classes after them alphabetically


def collect():
    rows = []
    for path in sorted(glob.glob(f'{BASE}/**/*.kt', recursive=True)):
        rel = path[len(BASE) + 1:]
        if rel.startswith('explore/'):
            continue
        src = open(path).read()
        cls_name = re.search(r'^class\s+(\w+)', src, re.M)
        if not cls_name:
            continue
        cls = cls_name.group(1)
        class_tags = set(re.findall(r'^@Tag\("([^"]+)"\)', src.split('class ')[0], re.M))
        ms = list(re.finditer(r'(?m)^([ \t]*)@(Test|RetryingTest)\b', src))
        for i, m in enumerate(ms):
            end = ms[i + 1].start() if i + 1 < len(ms) else len(src)
            body = src[m.start():end]
            pre = body.split('fun ')[0]
            above = '\n'.join(src[max(0, m.start() - 250):m.start()].split('\n')[-4:])
            both = pre + above
            dm = re.search(r'@DisplayName\("((?:[^"\\]|\\.)*)"\)', pre)
            name = (dm.group(1) if dm else re.search(r'fun\s+`?([^`(\n]+)', body).group(1)).replace('\\"', '"').replace('\\$', '$')
            im = re.match(r'\[([^\]]*)\]\s*(.*)', name)
            tid, title = (im.group(1), im.group(2)) if im else ('', name)
            tags = set(re.findall(r'@Tag\("([^"]+)"\)', both)) | class_tags
            skip = re.search(r'@Disabled\("((?:[^"\\]|\\.)*)"\)', both)
            device = not rel.startswith(NO_DEVICE_DIRS)
            if not device: suites, note = 'No device (every push)', ''
            elif skip: suites, note = 'Skipped', skip.group(1)[:150]
            elif 'sms' in tags: suites, note = 'Manual only (real SMS)', ''
            elif 'smoke' in tags: suites, note = 'Smoke + regression', ''
            else: suites, note = 'Regression only', ''
            rows.append(dict(group=GROUPS.get(cls, cls), cls=cls, id=tid, title=title, kind='Device' if device else 'No device', suites=suites, note=note))
    rank = lambda r: (ORDER.index(r['cls']) if r['cls'] in ORDER else len(ORDER), r['cls'])
    rows.sort(key=rank)  # stable: keeps the source order inside a class
    for i, r in enumerate(rows, 1):
        r['n'] = i
    return rows


def counts(rows):
    dev = [r for r in rows if r['kind'] == 'Device']
    return dict(dev=dev, nod=[r for r in rows if r['kind'] == 'No device'], smoke=[r for r in dev if r['suites'].startswith('Smoke')],
                reg=[r for r in dev if r['suites'] in ('Smoke + regression', 'Regression only')],
                skipped=[r for r in dev if r['suites'] == 'Skipped'], sms=[r for r in dev if r['suites'].startswith('Manual')])


def write_md_csv(rows, c, today):
    with open('docs/all-tests.csv', 'w', newline='') as fh:
        w = csv.writer(fh); w.writerow(['No', 'Group', 'Class', 'Scenario IDs', 'Test', 'Type', 'Suites', 'Note'])
        for r in rows: w.writerow([r['n'], r['group'], r['cls'], r['id'], r['title'], r['kind'], r['suites'], r['note']])
    md = [f'# All automated tests ({len(rows)})', '', f'Collected from the test source on {today}. Repository: `tms-driverapp-e2e-tests`. Regenerate with `python3 scripts/docs/generate_all_tests.py`.', '',
          '| Kind | Tests | Notes |', '|---|---|---|',
          f"| Device tests (emulator, staging) | {len(c['dev'])} | **{len(c['smoke'])} in the smoke suite** (`smokeTest`), {len(c['reg'])} in the regression (`regressionTest`, Tue/Thu), {len(c['skipped'])} skipped, {len(c['sms'])} manual only (real SMS) |",
          f"| No-device tests (stub servers) | {len(c['nod'])} | run on every push with `./gradlew test` |",
          f'| **Total** | **{len(rows)}** | exploration helpers are not counted (they assert nothing) |', '']
    cur = None
    for r in rows:
        if r['group'] != cur:
            cur = r['group']; md += ['', f'## {cur}', '', '| # | IDs | Test | Suites |', '|---|---|---|---|']
        md.append(f"| {r['n']} | {r['id'] or '-'} | {r['title'].replace('|', '/')} | {r['suites']}{(': ' + r['note']) if r['note'] else ''} |")
    open('docs/ALL_TESTS.md', 'w').write('\n'.join(md) + '\n')


def write_pdf(rows, c, today):
    try:
        from reportlab.lib.pagesizes import A4, landscape
        from reportlab.lib import colors
        from reportlab.lib.styles import ParagraphStyle
        from reportlab.lib.units import mm
        from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
    except ImportError:
        print('reportlab not installed: the PDF was not written'); return
    fonts = ('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')
    if not all(os.path.exists(f) for f in fonts):
        print('DejaVu fonts not found: the PDF was not written'); return
    pdfmetrics.registerFont(TTFont('DV', fonts[0])); pdfmetrics.registerFont(TTFont('DVB', fonts[1]))
    esc = lambda t: t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    h1 = ParagraphStyle('h1', fontName='DVB', fontSize=17, leading=21, spaceAfter=4)
    h2 = ParagraphStyle('h2', fontName='DVB', fontSize=11, leading=14, spaceBefore=10, spaceAfter=4, textColor=colors.HexColor('#1f3a5f'))
    sm = ParagraphStyle('sm', fontName='DV', fontSize=8, leading=10.2)
    smb = ParagraphStyle('smb', fontName='DVB', fontSize=8, leading=10.2, textColor=colors.white)
    body = ParagraphStyle('body', fontName='DV', fontSize=9, leading=12)
    doc = SimpleDocTemplate('docs/ALL_TESTS.pdf', pagesize=landscape(A4), leftMargin=12 * mm, rightMargin=12 * mm, topMargin=12 * mm, bottomMargin=12 * mm,
                            title='TMS Driver App - all automated tests', author='tms-driverapp-e2e-tests')
    head = lambda labels: [Paragraph(x, smb) for x in labels]
    grid = [('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1f3a5f')), ('GRID', (0, 0), (-1, -1), 0.3, colors.HexColor('#b8b8b8')), ('VALIGN', (0, 0), (-1, -1), 'TOP')]
    st = [Paragraph('TMS Driver App: all automated tests', h1),
          Paragraph(f"Collected from the test source on {today}. <b>{len(rows)} tests</b>: {len(c['dev'])} device tests (Appium, emulator, staging) and {len(c['nod'])} no-device tests (stub servers).", body), Spacer(1, 4)]
    summ = [head(('Kind', 'Tests', 'Notes')),
            [Paragraph('Device tests', sm), Paragraph(str(len(c['dev'])), sm), Paragraph(f"<b>{len(c['smoke'])} in the smoke suite</b> (smokeTest), {len(c['reg'])} in the regression (regressionTest, Tuesday and Thursday), {len(c['skipped'])} skipped, {len(c['sms'])} manual only (sends real SMS)", sm)],
            [Paragraph('No-device tests', sm), Paragraph(str(len(c['nod'])), sm), Paragraph('run on every push with ./gradlew test, no emulator or staging needed', sm)],
            [Paragraph('<b>Total</b>', sm), Paragraph(f'<b>{len(rows)}</b>', sm), Paragraph('exploration helpers that only record screens are not counted', sm)]]
    t = Table(summ, colWidths=[40 * mm, 20 * mm, 200 * mm]); t.setStyle(TableStyle(grid)); st.append(t)
    cur, buf = None, []
    def flush():
        nonlocal buf
        if buf:
            tb = Table([head(('#', 'Scenario IDs', 'Test', 'Suites'))] + buf, colWidths=[9 * mm, 42 * mm, 140 * mm, 56 * mm], repeatRows=1)
            tb.setStyle(TableStyle(grid + [('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#f3f6fa')])])); st.append(tb); buf = []
    for r in rows:
        if r['group'] != cur:
            flush(); cur = r['group']; st.append(Paragraph(esc(cur) + f"  <font size=8 color='#666666'>({r['cls']})</font>", h2))
        s = r['suites']; col = '#a33' if s == 'Skipped' else ('#b60' if s.startswith('Manual') else ('#0a6' if s.startswith('Smoke') else '#222'))
        buf.append([Paragraph(str(r['n']), sm), Paragraph(esc(r['id']) or '-', sm), Paragraph(esc(r['title']), sm), Paragraph(f"<font color='{col}'>{esc(s)}</font>", sm)])
    flush(); doc.build(st)


if __name__ == '__main__':
    os.makedirs('docs', exist_ok=True)
    today = datetime.date.today().isoformat()
    rows = collect(); c = counts(rows)
    write_md_csv(rows, c, today); write_pdf(rows, c, today)
    print(f"{len(rows)} tests: {len(c['dev'])} device ({len(c['smoke'])} smoke, {len(c['reg'])} regression, {len(c['skipped'])} skipped, {len(c['sms'])} manual) and {len(c['nod'])} no device")
