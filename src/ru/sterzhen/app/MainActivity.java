package ru.sterzhen.app;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String MEM_FILE = "sterzhen_nodes.tsv";
    private static final String SRC_FILE = "sterzhen_sources.tsv";

    static final class MemoryNode {
        final Set<String> conditions;
        final String action, checkRule;
        int okCount, failCount;
        boolean isCoreNode;
        MemoryNode(Set<String> c, String a, String r, int ok, int fail, boolean core) {
            conditions = new TreeSet<>(c); action = a;
            checkRule = (r == null || r.isEmpty()) ? "io_ok" : r;
            okCount = ok; failCount = fail; isCoreNode = core;
        }
        String key() { return String.join(";", conditions) + "||" + action.toLowerCase(Locale.ROOT); }
        double confidence() { int t = okCount + failCount; return t == 0 ? 0.0 : (double) okCount / t; }
        boolean isSolid() { return okCount >= 1 && confidence() >= 0.70; }
    }

    static final class Hypothesis {
        final Set<String> conditions;
        final String action, checkRule, origin;
        Hypothesis(Set<String> c, String a, String r, String o) {
            conditions = new TreeSet<>(c); action = a; checkRule = r; origin = o;
        }
    }

    static final class ProbeResult {
        final boolean passed, auto;
        final String report;
        ProbeResult(boolean p, boolean a, String r) { passed = p; auto = a; report = r; }
    }

    final class EnvironmentVerifier {
        long ramMb() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.availMem / (1024 * 1024);
            } catch (Exception e) { return 512; }
        }
        long diskMb() {
            try {
                StatFs s = new StatFs(Environment.getDataDirectory().getPath());
                return (s.getAvailableBlocksLong() * s.getBlockSizeLong()) / (1024 * 1024);
            } catch (Exception e) { return 1024; }
        }
        boolean netOk() {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                Network n = cm.getActiveNetwork();
                NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
                return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } catch (Exception e) { return false; }
        }
        Set<String> readHw() {
            Set<String> hw = new TreeSet<>();
            hw.add(ramMb() < 400 ? "озу=мало" : "озу=достаточно");
            hw.add(diskMb() < 300 ? "диск=мало" : "диск=достаточно");
            hw.add(netOk() ? "сеть=есть" : "сеть=нет");
            return hw;
        }
        ProbeResult verify(Hypothesis h) {
            if (h.checkRule == null || h.checkRule.isEmpty() || h.checkRule.equalsIgnoreCase("manual"))
                return new ProbeResult(false, false, "Ожидает ручной приёмки (manual)");
            List<String> log = new ArrayList<>();
            for (String raw : h.checkRule.split(";")) {
                String c = raw.trim().toLowerCase(Locale.ROOT);
                if (c.isEmpty()) continue;
                if (c.startsWith("ram_mb>")) {
                    long lim = Long.parseLong(c.substring(7)), act = ramMb();
                    if (act <= lim) return new ProbeResult(false, true, "СБОЙ ОЗУ: " + act + "<=" + lim);
                    log.add("ОЗУ=" + act + "МБ");
                } else if (c.startsWith("disk_mb>")) {
                    long lim = Long.parseLong(c.substring(8)), act = diskMb();
                    if (act <= lim) return new ProbeResult(false, true, "СБОЙ ДИСКА: " + act + "<=" + lim);
                    log.add("Диск=" + act + "МБ");
                } else if (c.equals("net_ok")) {
                    if (!netOk()) return new ProbeResult(false, true, "СБОЙ: сеть недоступна");
                    log.add("Сеть=активна");
                } else if (c.equals("io_ok")) {
                    long t0 = System.nanoTime();
                    File pf = new File(getCacheDir(), "probe.tmp");
                    try (FileOutputStream fos = new FileOutputStream(pf)) {
                        fos.write("OK".getBytes(StandardCharsets.UTF_8)); fos.getFD().sync();
                    } catch (Exception e) { return new ProbeResult(false, true, "СБОЙ ФС");
                    } finally { pf.delete(); }
                    log.add("ФС=" + ((System.nanoTime() - t0) / 1_000_000) + "мс");
                } else return new ProbeResult(false, false, "Правило '" + c + "' -> ручная приёмка");
            }
            return new ProbeResult(true, true, "ПРОВЕРЕНО ДАТЧИКАМИ [" + String.join(", ", log) + "]");
        }
    }

    final class SourceExplorer {
        List<Hypothesis> discover(Set<String> query, Set<String> unknown, String urlStr) {
            List<Hypothesis> pool = new ArrayList<>();
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            for (File f : new File[]{new File(getFilesDir(), SRC_FILE), new File(dl, SRC_FILE)}) {
                if (f != null && f.exists()) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                        parseTsv(br, "Файл " + f.getName(), pool);
                    } catch (Exception ignored) {}
                }
            }
            if (urlStr != null && urlStr.startsWith("http")) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
                    c.setConnectTimeout(4000);
                    if (c.getResponseCode() == 200)
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                            parseTsv(br, "Сетевая база", pool);
                        }
                } catch (Exception ignored) {}
            }
            pool.add(new Hypothesis(parse("сеть=нет;диск=достаточно"), "Локальная очередь на диске с отложенной синхронизацией", "disk_mb>100;io_ok", "Резервный фонд"));
            pool.add(new Hypothesis(parse("озу=мало;диск=достаточно"), "Потоковая построчная обработка файла без загрузки в ОЗУ", "disk_mb>50;io_ok", "Резервный фонд"));
            pool.add(new Hypothesis(parse("озу=мало;диск=мало"), "Кольцевой буфер фиксированного размера с перезаписью", "ram_mb>20;io_ok", "Резервный фонд"));
            pool.add(new Hypothesis(parse("сеть=есть;озу=достаточно"), "Пакетная загрузка с проверкой контрольных сумм в памяти", "net_ok;ram_mb>200;io_ok", "Резервный фонд"));

            List<Hypothesis> matched = new ArrayList<>();
            for (Hypothesis h : pool)
                for (String q : query) if (h.conditions.contains(q)) { matched.add(h); break; }

            if (matched.isEmpty() && !unknown.isEmpty()) {
                for (String tc : unknown) {
                    String term = tc.contains("=") ? tc.split("=")[0].trim() : tc;
                    if (term.length() < 2) continue;
                    try {
                        String u = "https://ru.wikipedia.org/w/api.php?action=opensearch&search=" + URLEncoder.encode(term, "UTF-8") + "&limit=1&format=json";
                        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                        c.setRequestProperty("User-Agent", "Sterzhen/2.0");
                        if (c.getResponseCode() == 200) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                            JSONArray a = new JSONArray(br.readLine()), t = a.optJSONArray(1), l = a.optJSONArray(3);
                            if (t != null && t.length() > 0)
                                matched.add(new Hypothesis(parse(tc), "Изучить «" + t.optString(0) + "» (" + (l != null ? l.optString(0) : "") + ")", "manual", "Wikipedia API"));
                        }
                    } catch (Exception ignored) {}
                }
            }
            return matched;
        }
        void parseTsv(BufferedReader br, String orig, List<Hypothesis> out) throws Exception {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split("\t");
                if (p.length >= 2 && !p[0].startsWith("#"))
                    out.add(new Hypothesis(parse(p[0]), p[1], p.length >= 3 ? p[2] : "manual", orig));
            }
        }
    }

    final class SterzhenCore {
        final List<MemoryNode> nodes = new ArrayList<>();
        final File file;
        SterzhenCore(File f) { file = f; load(); }

        synchronized boolean isBlocked(Set<String> q, String act) {
            for (MemoryNode n : nodes)
                if (n.action.equalsIgnoreCase(act) && n.failCount > n.okCount && q.containsAll(n.conditions)) return true;
            return false;
        }
        synchronized void record(Set<String> c, String a, String r, boolean ok, boolean core) {
            MemoryNode pr = new MemoryNode(c, a, r, 0, 0, core);
            for (MemoryNode ex : nodes) {
                if (ex.key().equals(pr.key())) {
                    if (ok) ex.okCount++; else ex.failCount++;
                    if (core) ex.isCoreNode = true;
                    save(); return;
                }
            }
            nodes.add(new MemoryNode(c, a, r, ok ? 1 : 0, ok ? 0 : 1, core));
            save();
        }
        synchronized int consolidate() {
            int cnt = 0;
            List<MemoryNode> snap = new ArrayList<>(nodes);
            for (int i = 0; i < snap.size(); i++) {
                MemoryNode a = snap.get(i);
                if (!a.isSolid() || a.failCount > 0) continue;
                for (int j = i + 1; j < snap.size(); j++) {
                    MemoryNode b = snap.get(j);
                    if (!b.isSolid() || b.failCount > 0 || !a.action.equalsIgnoreCase(b.action)) continue;
                    Set<String> inter = new TreeSet<>(a.conditions); inter.retainAll(b.conditions);
                    if (inter.isEmpty() || inter.size() == a.conditions.size()) continue;
                    boolean bad = false;
                    for (MemoryNode neg : snap) if (neg.failCount > 0 && neg.conditions.containsAll(inter)) bad = true;
                    if (bad) continue;
                    record(inter, a.action, a.checkRule, true, true);
                    cnt++;
                }
            }
            return cnt;
        }
        String runCycle(Set<String> in, String url, EnvironmentVerifier ver, SourceExplorer exp) {
            if (in.isEmpty()) return "Задайте условия задачи.";
            StringBuilder log = new StringBuilder("=== 1. ФОКУСИРОВАНИЕ ПО ПАМЯТИ ===\nВход: [" + String.join("; ", in) + "]\n");
            MemoryNode best = null; double bestSc = -1;
            Set<String> known = new LinkedHashSet<>();
            synchronized (this) {
                for (MemoryNode n : nodes) {
                    known.addAll(n.conditions);
                    if (!n.isSolid() || isBlocked(in, n.action)) continue;
                    int m = 0;
                    for (String c : n.conditions) if (in.contains(c)) m++;
                    if (m == 0) continue;
                    double r = (double) m / n.conditions.size(), cov = (double) m / in.size();
                    double sc = r * 0.45 + cov * 0.35 + n.confidence() * 0.20 + (n.isCoreNode ? 0.10 : 0);
                    if (r >= 0.80 && sc > bestSc) { bestSc = sc; best = n; }
                }
            }
            if (best != null) {
                return log.append("НАЙДЕН УЗЕЛ ").append(best.isCoreNode ? "[ФОКУС-ЯДРО]:\n" : "[ПРЕЦЕДЕНТ]:\n")
                        .append("-> Решение: ").append(best.action).append("\n-> Условия: [").append(String.join("; ", best.conditions))
                        .append(String.format(Locale.US, "]\n-> Надёжность: +%d/-%d (%.0f%%)\n", best.okCount, best.failCount, best.confidence() * 100)).toString();
            }
            log.append("Подтверждённого узла нет. Поиск...\n\n=== 2. ПОИСК И ДАТЧИКИ ===\n");
            Set<String> enr = new TreeSet<>(in), hw = ver.readHw(), unk = new TreeSet<>();
            enr.addAll(hw);
            for (String q : in) if (!known.contains(q)) unk.add(q);
            log.append("Среда: [").append(String.join("; ", hw)).append("]\n");
            List<Hypothesis> cands = exp.discover(enr, unk, url);
            if (cands.isEmpty()) return log.append("Гипотез не найдено.").toString();
            log.append("Кандидатов: ").append(cands.size()).append("\n\n=== 3. ПРОВЕРКА И НАРАБОТКА ===\n");
            int okCnt = 0;
            for (Hypothesis h : cands) {
                log.append("* Из «").append(h.origin).append("»:\n  ").append(h.action).append("\n");
                if (isBlocked(enr, h.action)) { log.append("  ОТВЕРГНУТО (ранее сбой)\n\n"); continue; }
                ProbeResult pr = ver.verify(h);
                log.append("  Испытание: ").append(pr.report).append("\n");
                if (pr.auto) {
                    record(h.conditions, h.action, h.checkRule, pr.passed, false);
                    if (pr.passed) { okCnt++; log.append("  Статус: УЗЕЛ ПОДТВЕРЖДЁН (+1)\n\n"); }
                    else log.append("  Статус: ОТКАЗ (-1)\n\n");
                } else {
                    final String act = h.action;
                    runOnUiThread(new Runnable() { public void run() { inAct.setText(act); } });
                    log.append("  Статус: НА РУЧНУЮ ПРИЁМКУ\n\n");
                }
            }
            if (okCnt > 0) {
                int cf = consolidate();
                if (cf > 0) log.append("=== 4. ФОКУС-УЗЛЫ ===\nВыделено ядер: ").append(cf).append("\n");
            }
            return log.toString();
        }
        synchronized String dump() {
            StringBuilder sb = new StringBuilder("=== ПАМЯТЬ СТЕРЖНЯ (" + nodes.size() + ") ===\n");
            for (int i = 0; i < nodes.size(); i++) {
                MemoryNode n = nodes.get(i);
                sb.append(i + 1).append(". ").append(n.isCoreNode ? "[ФОКУС-ЯДРО] " : "[УЗЕЛ] ")
                  .append("[").append(String.join("; ", n.conditions)).append("]\n   -> ").append(n.action)
                  .append(" (+").append(n.okCount).append("/-").append(n.failCount).append(")\n");
            }
            return sb.toString();
        }
        void save() {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (MemoryNode n : nodes)
                    bw.write(String.join(";", n.conditions) + "\t" + n.action.replace('\t', ' ') + "\t" + n.checkRule + "\t" + n.okCount + "\t" + n.failCount + "\t" + (n.isCoreNode ? "1" : "0") + "\n");
            } catch (Exception ignored) {}
        }
        void load() {
            nodes.clear(); if (!file.exists()) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    String[] c = l.split("\t");
                    if (c.length >= 6) nodes.add(new MemoryNode(parse(c[0]), c[1], c[2], Integer.parseInt(c[3]), Integer.parseInt(c[4]), "1".equals(c[5])));
                }
            } catch (Exception ignored) {}
        }
    }

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private EnvironmentVerifier ver; private SourceExplorer exp; private SterzhenCore core;
    private EditText inCond, inUrl, inAct; private TextView outLog;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ver = new EnvironmentVerifier(); exp = new SourceExplorer(); core = new SterzhenCore(new File(getFilesDir(), MEM_FILE));
        ScrollView sc = new ScrollView(this); sc.setBackgroundColor(Color.parseColor("#0D1117"));
        LinearLayout rt = new LinearLayout(this); rt.setOrientation(LinearLayout.VERTICAL); rt.setPadding(24, 32, 24, 44);
        TextView t = new TextView(this); t.setText("СТЕРЖЕНЬ 2.0 :: АВТОКОНТУР"); t.setTextSize(18f); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextColor(Color.parseColor("#58A6FF")); rt.addView(t);
        inCond = ed("Условия (сеть=нет;диск=достаточно)"); inCond.setText(String.join(";", ver.readHw())); rt.addView(inCond);
        inUrl = ed("URL внешней базы .tsv"); rt.addView(inUrl);
        inAct = ed("Решение для ручной приёмки (+1 / -1)"); rt.addView(inAct);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); lp.rightMargin = 8;
        LinearLayout r1 = new LinearLayout(this); r1.setPadding(0, 10, 0, 6);
        Button bHw = bt("СРЕДА", "#1F6FEB"), bRun = bt("ЗАПУСК КОНТУРА", "#238636");
        r1.addView(bHw, lp); r1.addView(bRun, lp); rt.addView(r1);

        LinearLayout r2 = new LinearLayout(this); r2.setPadding(0, 0, 0, 12);
        Button bOk = bt("ПРИНЯТЬ +1", "#059669"), bNo = bt("ОТКАЗ -1", "#DA3633"), bMem = bt("ПАМЯТЬ", "#30363D");
        r2.addView(bOk, lp); r2.addView(bNo, lp); r2.addView(bMem, lp); rt.addView(r2);

        outLog = new TextView(this); outLog.setTypeface(Typeface.MONOSPACE); outLog.setTextSize(12f); outLog.setTextColor(Color.parseColor("#E6EDF3")); outLog.setBackgroundColor(Color.parseColor("#161B22")); outLog.setPadding(18, 18, 18, 18); rt.addView(outLog);

        bHw.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { inCond.setText(String.join(";", ver.readHw())); runCyc(); } });
        bRun.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { runCyc(); } });
        bOk.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { man(true); } });
        bNo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { man(false); } });
        bMem.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { outLog.setText(core.dump()); } });

        runCyc(); sc.addView(rt); setContentView(sc);
    }
    void runCyc() {
        final Set<String> c = parse(inCond.getText().toString()); final String u = inUrl.getText().toString().trim();
        outLog.setText("Выполняется цикл...\n");
        exec.execute(new Runnable() { public void run() { final String r = core.runCycle(c, u, ver, exp); runOnUiThread(new Runnable() { public void run() { outLog.setText(r); } }); } });
    }
    void man(boolean ok) {
        Set<String> c = parse(inCond.getText().toString()); String a = inAct.getText().toString().trim();
        if (!c.isEmpty() && !a.isEmpty()) { core.record(c, a, "manual", ok, false); core.consolidate(); outLog.setText(core.dump()); }
    }
    EditText ed(String h) { EditText e = new EditText(this); e.setHint(h); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE); e.setTextSize(13f); return e; }
    Button bt(String t, String c) { Button b = new Button(this); b.setText(t); b.setTextSize(11f); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.parseColor(c)); return b; }
    static Set<String> parse(String r) { Set<String> s = new TreeSet<>(); if (r != null) for (String p : r.split(";")) if (!p.trim().isEmpty()) s.add(p.trim().toLowerCase(Locale.ROOT)); return s; }
}
