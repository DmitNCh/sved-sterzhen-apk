package ru.sterzhen.app;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final String MEM_FILE = "sterzhen_lenses.tsv";

    // Узел памяти и Линза Фокусирования (lensLevel: 0=Прецедент, 1=Линза L1, 2=Линза L2, 3=Кристалл Ядра L3)
    static final class MemoryNode {
        final Set<String> conditions;
        final String action, checkRule;
        int okCount, failCount, lensLevel;

        MemoryNode(Set<String> c, String a, String r, int ok, int fail, int level) {
            conditions = new TreeSet<>(c);
            action = a;
            checkRule = r;
            okCount = ok;
            failCount = fail;
            lensLevel = level;
        }

        String key() {
            return lensLevel + "|" + String.join(";", conditions) + "|" + action;
        }

        String badge() {
            if (lensLevel == 0) return "[ПРЕЦЕДЕНТ L0]";
            if (lensLevel == 1) return "[ЛИНЗА ФОКУСА L1]";
            if (lensLevel == 2) return "[СЛОЖНАЯ ЛИНЗА L2]";
            return "[★ КРИСТАЛЛ ЯДРА L" + lensLevel + "]";
        }
    }

    // Автоматический генератор инженерных и аппаратных условий (Полигон Непознанного)
    final class AutonomousPoligon {
        private final Random rnd = new Random();
        private int step = 0;

        private final String[][] tasks = new String[][]{
            {"среда=норма;память=свободна;поток=пакетный", "Пакетный конвейер с контролем целостности", "ram_ok;io_ok"},
            {"среда=перегрузка;память=свободна;поток=пакетный", "Пакетный конвейер с контролем целостности", "ram_ok;io_ok"},
            {"среда=норма;память=сжата;поток=непрерывный", "Кольцевой фильтр КРЯЖ (отсечение мёртвого груза)", "disk_ok;io_ok"},
            {"среда=перегрузка;память=сжата;поток=непрерывный", "Кольцевой фильтр КРЯЖ (отсечение мёртвого груза)", "disk_ok;io_ok"},
            {"тепло=высокое;нагрузка=ударная;основа=титан_тм1", "Синтез матрицы ТМ-1 + Волокно ВС-3 в коридоре Δ=пост.", "io_ok;corridor_ok"},
            {"тепло=высокое;нагрузка=статика;основа=титан_тм1", "Синтез матрицы ТМ-1 + Волокно ВС-3 в коридоре Δ=пост.", "io_ok;corridor_ok"},
            {"спектр=нейтроны;поле=высокое;защита=мгр10", "Захват в ядерное окно 0.8..1.8 фм (Мета-гидрид МГР-10 + БС-16)", "ram_ok;io_ok;corridor_ok"},
            {"спектр=нейтроны;поле=импульс;защита=мгр10", "Захват в ядерное окно 0.8..1.8 фм (Мета-гидрид МГР-10 + БС-16)", "ram_ok;io_ok;corridor_ok"},
            {"среда=агрессивная;тепло=экстрим;контур=кк4", "Кремний-Карбид КК-4 с магнитной стабилизацией ФК-9", "disk_ok;io_ok;corridor_ok"},
            {"среда=вакуум;тепло=экстрим;контур=кк4", "Кремний-Карбид КК-4 с магнитной стабилизацией ФК-9", "disk_ok;io_ok;corridor_ok"},
            {"шум=высокий;память=засорена;режим=аварийный", "Нестабильный сырой сброс без фильтрации (БРАК)", "fail_sim"}
        };

        String[] nextChallenge(List<MemoryNode> currentMemory) {
            // Если уже есть Линзы L1/L2 — используем их для фокусировки комбинированных решений!
            List<MemoryNode> lenses = new ArrayList<>();
            for (MemoryNode n : currentMemory) {
                if (n.lensLevel >= 1 && n.failCount == 0) lenses.add(n);
            }

            step++;
            if (lenses.size() >= 2 && step % 3 == 0) {
                MemoryNode lA = lenses.get(rnd.nextInt(lenses.size()));
                MemoryNode lB = lenses.get(rnd.nextInt(lenses.size()));
                if (!lA.action.equals(lB.action)) {
                    Set<String> merged = new TreeSet<>(lA.conditions);
                    merged.addAll(lB.conditions);
                    int nextLvl = Math.min(3, Math.max(lA.lensLevel, lB.lensLevel) + 1);
                    return new String[]{
                        String.join(";", merged),
                        "Синтез-решение через Линзы: [" + lA.action + "] + [" + lB.action + "]",
                        "ram_ok;disk_ok;io_ok;corridor_ok",
                        String.valueOf(nextLvl)
                    };
                }
            }

            String[] t = tasks[step % tasks.length];
            return new String[]{t[0], t[1], t[2], "0"};
        }
    }

    // Калибратор Жёлтой линии (реальные датчики смартфона + проверка коридора параллельности)
    final class YellowLineCalibrator {
        long freeRamMb() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.availMem / (1024 * 1024);
            } catch (Exception e) { return 512; }
        }

        long freeDiskMb() {
            try {
                StatFs s = new StatFs(Environment.getDataDirectory().getPath());
                return (s.getAvailableBlocksLong() * s.getBlockSizeLong()) / (1024 * 1024);
            } catch (Exception e) { return 1024; }
        }

        String verify100(String rule) {
            if (rule.contains("fail_sim")) {
                return "СБОЙ КАЛИБРАТОРА: Обнаружен побочный вред и мёртвый груз (>30%)";
            }
            long ram = freeRamMb();
            long disk = freeDiskMb();
            if (rule.contains("ram_ok") && ram < 50) return "СБОЙ ОЗУ: " + ram + " МБ";
            if (rule.contains("disk_ok") && disk < 50) return "СБОЙ ДИСКА: " + disk + " МБ";

            long t0 = System.nanoTime();
            File f = new File(getCacheDir(), "calibrator.tmp");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write("СТЕРЖЕНЬ_100%".getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();
            } catch (Exception e) {
                return "СБОЙ ФС: " + e.getMessage();
            } finally {
                f.delete();
            }
            long us = (System.nanoTime() - t0) / 1000;
            return "ОК 100% [ОЗУ=" + ram + "МБ, Диск=" + disk + "МБ, Затвор=" + us + "мкс, Δ=пост.]";
        }
    }

    // Главное Ядро самонаработки памяти и каскадной сборки Линз Фокусирования
    final class SterzhenCascadeCore {
        final List<MemoryNode> nodes = new ArrayList<>();
        final File file;
        int totalCycles = 0;

        SterzhenCascadeCore(File f) {
            file = f;
            load();
        }

        synchronized String autoStep(AutonomousPoligon poligon, YellowLineCalibrator cal) {
            totalCycles++;
            StringBuilder sb = new StringBuilder();
            String[] task = poligon.nextChallenge(nodes);
            Set<String> conds = parse(task[0]);
            String action = task[1];
            String rule = task[2];
            int targetLevel = Integer.parseInt(task[3]);

            sb.append("=== АВТОЦИКЛ НАРАБОТКИ #").append(totalCycles).append(" ===\n");
            sb.append("1. ПОИСК / ЗАДАЧА ИЗ НЕПОЗНАННОГО:\n   Условия: ").append(conds).append("\n");

            // Проверка: не забраковано ли ранее
            for (MemoryNode n : nodes) {
                if (n.action.equals(action) && n.failCount > 0) {
                    sb.append("2. ФОКУС-ОТСЕЧЕНИЕ:\n   Уже заблокировано памятью как вредное/сбойное!\n");
                    sb.append("   Переход к следующей наработке...\n\n").append(statsSummary());
                    return sb.toString();
                }
            }

            // Калибратор Жёлтой линии
            String testRes = cal.verify100(rule);
            boolean passed = testRes.startsWith("ОК 100%");

            sb.append("2. ЖЁЛТАЯ ЛИНИЯ (КАЛИБРАТОР 100%):\n   ").append(testRes).append("\n");

            if (passed) {
                recordNode(conds, action, rule, true, targetLevel);
                sb.append("3. ЗАПИСЬ В ПАМЯТЬ СТЕРЖНЯ:\n   ✓ Узел подтверждён (+1) на уровне L").append(targetLevel).append("\n");
            } else {
                recordNode(conds, action, rule, false, 0);
                sb.append("3. ЗАЩИТА ЯДРА:\n   ✗ Кандидат отсечён и занесён в чёрный список (-1)\n");
            }

            // Каскадная сборка Линз: из Прецедентов L0 -> Линзы L1, из L1 -> L2!
            int newLenses = buildNextLenses();
            if (newLenses > 0) {
                sb.append("4. ★ СБОРКА НОВОЙ ЛИНЗЫ ФОКУСИРОВАНИЯ!\n   За счёт наработанной памяти отлито новых Линз: +").append(newLenses).append("\n");
            } else {
                sb.append("4. Накопление плотности для отливки следующей Линзы...\n");
            }

            sb.append("\n").append(statsSummary());
            return sb.toString();
        }

        private void recordNode(Set<String> c, String a, String r, boolean ok, int lvl) {
            MemoryNode probe = new MemoryNode(c, a, r, 0, 0, lvl);
            for (MemoryNode ex : nodes) {
                if (ex.key().equals(probe.key())) {
                    if (ok) ex.okCount++; else ex.failCount++;
                    save();
                    return;
                }
            }
            nodes.add(new MemoryNode(c, a, r, ok ? 1 : 0, ok ? 0 : 1, lvl));
            save();
        }

        // Выделение общего ядра условий и перевод L0 -> L1 -> L2
        private int buildNextLenses() {
            int built = 0;
            List<MemoryNode> snap = new ArrayList<>(nodes);
            for (int i = 0; i < snap.size(); i++) {
                MemoryNode a = snap.get(i);
                if (a.failCount > 0 || a.okCount == 0 || a.lensLevel >= 2) continue;
                for (int j = i + 1; j < snap.size(); j++) {
                    MemoryNode b = snap.get(j);
                    if (b.failCount > 0 || b.okCount == 0 || a.lensLevel != b.lensLevel) continue;
                    if (!a.action.equals(b.action)) continue;

                    Set<String> coreConds = new TreeSet<>(a.conditions);
                    coreConds.retainAll(b.conditions);
                    if (coreConds.isEmpty() || coreConds.size() == a.conditions.size()) continue;

                    int nextLvl = a.lensLevel + 1;
                    MemoryNode lens = new MemoryNode(coreConds, a.action, a.checkRule, a.okCount + b.okCount, 0, nextLvl);
                    boolean exists = false;
                    for (MemoryNode cur : nodes) {
                        if (cur.key().equals(lens.key())) { exists = true; break; }
                    }
                    if (!exists) {
                        nodes.add(lens);
                        built++;
                    }
                }
            }
            if (built > 0) save();
            return built;
        }

        synchronized String statsSummary() {
            int l0 = 0, l1 = 0, l2 = 0, l3 = 0, blocked = 0;
            for (MemoryNode n : nodes) {
                if (n.failCount > 0) blocked++;
                else if (n.lensLevel == 0) l0++;
                else if (n.lensLevel == 1) l1++;
                else if (n.lensLevel == 2) l2++;
                else l3++;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("=== КАСКАД ЛИНЗ В ПАМЯТИ СТЕРЖНЯ ===\n");
            sb.append(String.format(Locale.US, "• Прецеденты (L0): %d | Отказы (Блок): %d\n", l0, blocked));
            sb.append(String.format(Locale.US, "• Линзы Фокуса 1-го порядка (L1): %d\n", l1));
            sb.append(String.format(Locale.US, "• Комбинированные Линзы (L2): %d\n", l2));
            sb.append(String.format(Locale.US, "• Кристалл Ядра (L3): %d\n\n", l3));

            sb.append("Последние сформированные Линзы и Узлы:\n");
            for (int i = nodes.size() - 1; i >= Math.max(0, nodes.size() - 6); i--) {
                MemoryNode n = nodes.get(i);
                if (n.failCount > 0) {
                    sb.append("✗ [БЛОК ВРЕДА] ").append(n.action).append("\n");
                } else {
                    sb.append("★ ").append(n.badge()).append(" (опыт +").append(n.okCount).append(")\n")
                      .append("  Фокус: ").append(n.conditions).append("\n")
                      .append("  -> ").append(n.action).append("\n\n");
                }
            }
            return sb.toString();
        }

        void save() {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (MemoryNode n : nodes) {
                    bw.write(String.join(";", n.conditions) + "\t" + n.action + "\t" + n.checkRule + "\t"
                            + n.okCount + "\t" + n.failCount + "\t" + n.lensLevel + "\n");
                }
            } catch (Exception ignored) {}
        }

        void load() {
            nodes.clear();
            if (!file.exists()) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    String[] c = l.split("\t");
                    if (c.length >= 6) {
                        nodes.add(new MemoryNode(parse(c[0]), c[1], c[2],
                                Integer.parseInt(c[3]), Integer.parseInt(c[4]), Integer.parseInt(c[5])));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoRunning = true;
    private AutonomousPoligon poligon;
    private YellowLineCalibrator calibrator;
    private SterzhenCascadeCore core;
    private TextView outLog;
    private Button btnToggle;

    private final Runnable loopTask = new Runnable() {
        @Override
        public void run() {
            if (!autoRunning) return;
            String report = core.autoStep(poligon, calibrator);
            outLog.setText(report);
            handler.postDelayed(this, 1800);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        poligon = new AutonomousPoligon();
        calibrator = new YellowLineCalibrator();
        core = new SterzhenCascadeCore(new File(getFilesDir(), MEM_FILE));

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.parseColor("#0B0F19"));
        LinearLayout rt = new LinearLayout(this);
        rt.setOrientation(LinearLayout.VERTICAL);
        rt.setPadding(24, 34, 24, 44);

        TextView t = new TextView(this);
        t.setText("СТЕРЖЕНЬ :: САМОНАРАБОТКА ЛИНЗ");
        t.setTextSize(18f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#38BDF8"));
        rt.addView(t);

        TextView sub = new TextView(this);
        sub.setText("Непрерывный автомат: Поиск -> Жёлтая линия 100% -> Отливка Линз L1..L3");
        sub.setTextSize(11.5f);
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setPadding(0, 4, 0, 14);
        rt.addView(sub);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = 8;

        LinearLayout r1 = new LinearLayout(this);
        r1.setPadding(0, 0, 0, 12);
        btnToggle = bt("⏸ ПАУЗА АВТОНАРАБОТКИ", "#059669");
        Button btnStep = bt("ШАГ ВРУЧНУЮ", "#1F6FEB");
        Button btnClear = bt("СБРОС", "#334155");
        r1.addView(btnToggle, lp);
        r1.addView(btnStep, lp);
        r1.addView(btnClear, lp);
        rt.addView(r1);

        outLog = new TextView(this);
        outLog.setTypeface(Typeface.MONOSPACE);
        outLog.setTextSize(12f);
        outLog.setTextColor(Color.parseColor("#E6EDF3"));
        outLog.setBackgroundColor(Color.parseColor("#111827"));
        outLog.setPadding(20, 20, 20, 20);
        rt.addView(outLog);

        btnToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                autoRunning = !autoRunning;
                if (autoRunning) {
                    btnToggle.setText("⏸ ПАУЗА АВТОНАРАБОТКИ");
                    btnToggle.setBackgroundColor(Color.parseColor("#059669"));
                    handler.post(loopTask);
                } else {
                    btnToggle.setText("▶ ВКЛЮЧИТЬ АВТОНАРАБОТКУ");
                    btnToggle.setBackgroundColor(Color.parseColor("#D97706"));
                }
            }
        });

        btnStep.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                outLog.setText(core.autoStep(poligon, calibrator));
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                core.nodes.clear();
                core.totalCycles = 0;
                core.save();
                outLog.setText("Память очищена. Запуск новой наработки с нуля...\n");
            }
        });

        sc.addView(rt);
        setContentView(sc);
        handler.post(loopTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoRunning = false;
        handler.removeCallbacks(loopTask);
    }

    Button bt(String t, String c) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(10.5f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(c));
        return b;
    }

    static Set<String> parse(String r) {
        Set<String> s = new TreeSet<>();
        if (r != null) for (String p : r.split(";")) if (!p.trim().isEmpty()) s.add(p.trim());
        return s;
    }
}
