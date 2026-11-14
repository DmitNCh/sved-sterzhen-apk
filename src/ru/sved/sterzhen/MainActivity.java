package ru.sved.sterzhen;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    static class Sterzhen {
        String name, cls;
        double odnorod, ravnomer;
        int t, n, p, limit;
        double polezn, optim, ploshad;

        Sterzhen(String name, String cls, double o, double r, int t, int n, int p,
                 double pol, double opt, double pl, int lim) {
            this.name = name; this.cls = cls;
            this.odnorod = o; this.ravnomer = r;
            this.t = t; this.n = n; this.p = p;
            this.polezn = pol; this.optim = opt; this.ploshad = pl;
            this.limit = lim;
        }

        double kryazhDensity() {
            return ploshad == 0 ? 0 : (polezn + optim) / ploshad;
        }
    }

    static class CorridorView extends View {
        boolean parallel = true;
        int loadRatio = 60;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CorridorView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Color.parseColor("#161B22"));
            float w = getWidth(), h = getHeight();
            float x0 = 36f, xSplit = w * 0.72f, xEnd = w - 30f;
            float yTop = h * 0.28f, yBot = h * 0.72f, yMid = h * 0.50f;

            paint.setStrokeWidth(5f);
            paint.setColor(Color.parseColor("#3FB950"));
            c.drawLine(x0, yTop, xSplit, yTop, paint);
            c.drawLine(x0, yBot, xSplit, yBot, paint);

            paint.setColor(Color.parseColor("#F85149"));
            c.drawLine(xSplit, yTop, xEnd, yMid, paint);
            c.drawLine(xSplit, yBot, xEnd, yMid, paint);

            float curX = x0 + (xEnd - x0) * Math.min(100, loadRatio) / 100f;
            paint.setColor(parallel ? Color.parseColor("#58A6FF") : Color.parseColor("#FF7B72"));
            c.drawCircle(curX, yMid, 13f, paint);

            paint.setTextSize(25f);
            paint.setColor(Color.parseColor("#C9D1D9"));
            c.drawText("Коридор Δ=пост. (Материал)", x0, yTop - 14f, paint);
            c.drawText("Δ→0 (Предел)", xSplit - 24f, h - 14f, paint);
        }
    }

    private final List<Sterzhen> base = new ArrayList<>();
    private int targetT = 75, targetN = 80, targetP = 60;
    private double kryazhMin = 1.5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBase();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0D1117"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 44, 32, 60);

        root.addView(label("ОС СВЕД :: ЯДРО СТЕРЖЕНЬ", 19f, "#58A6FF"));
        root.addView(label("ГРЯДА | Фильтр КРЯЖ | Закон параллельных прямых", 12.5f, "#8B949E"));

        final CorridorView graph = new CorridorView(this);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 250);
        gp.topMargin = 20; gp.bottomMargin = 20;
        root.addView(graph, gp);

        root.addView(makeSlider("Целевое Тепло (Т): ", 75, 160, 0));
        root.addView(makeSlider("Целевая Нагрузка (Н): ", 80, 160, 1));
        root.addView(makeSlider("Целевое Поле (П): ", 60, 160, 2));
        root.addView(makeSlider("Порог плотности КРЯЖ (x0.1): ", 15, 32, 3));

        LinearLayout btns = new LinearLayout(this);
        Button btnRun = new Button(this);
        btnRun.setText("СОБРАТЬ МАТЕРИАЛ");
        Button btnCode = new Button(this);
        btnCode.setText("КОД ЛЕКСИКОНА");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btns.addView(btnRun, bp);
        btns.addView(btnCode, bp);
        root.addView(btns);

        final TextView out = label("", 13f, "#E6EDF3");
        out.setPadding(22, 22, 22, 22);
        out.setBackgroundColor(Color.parseColor("#161B22"));
        root.addView(out);

        btnRun.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                out.setText(synthesize(graph));
                graph.invalidate();
            }
        });
        btnCode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                out.setText(readLexicon());
            }
        });

        out.setText(synthesize(graph));
        scroll.addView(root);
        setContentView(scroll);
    }

    private View makeSlider(final String prefix, int init, int max, final int idx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView tv = label(prefix + init, 13.5f, "#C9D1D9");
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(init);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tv.setText(prefix + p);
                if (idx == 0) targetT = p;
                else if (idx == 1) targetN = p;
                else if (idx == 2) targetP = p;
                else kryazhMin = p / 10.0;
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(tv);
        box.addView(sb);
        return box;
    }

    private TextView label(String text, float size, String hexColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(Color.parseColor(hexColor));
        tv.setPadding(0, 6, 0, 6);
        return tv;
    }

    private void initBase() {
        base.clear();
        // Базовые опорные узлы и компенсаторы:
        // (Наименование, Категория, Однородность, Равномерность, Т, Н, П, Полезность, Оптимизация, Площадь_Памяти, Предел_Схождения)
        base.add(new Sterzhen("Титан-Матрица ТМ-1", "МАТЕРИАЛ", 0.94, 0.91, 50, 60, 35, 18.0, 12.0, 10.0, 260));
        base.add(new Sterzhen("Кремний-Карбид КК-4", "МАТЕРИАЛ", 0.92, 0.89, 65, 45, 30, 16.0, 11.0, 10.0, 250));
        base.add(new Sterzhen("Керамика-Щит КЩ-7", "ВЕЩЕСТВО", 0.89, 0.86, 45, 20, 15, 15.0, 9.0, 10.0, 90));
        base.add(new Sterzhen("Волокно-Стержень ВС-3", "МАТЕРИАЛ", 0.95, 0.93, 15, 45, 15, 17.0, 11.0, 10.0, 95));
        base.add(new Sterzhen("Феррит-Контур ФК-9", "МАТЕРИЯ", 0.91, 0.90, 15, 15, 45, 14.0, 10.0, 9.0, 85));
        base.add(new Sterzhen("Шлаковый Примесь-Блок", "ВЕЩЕСТВО", 0.55, 0.50, 20, 20, 20, 5.0, 3.0, 10.0, 60));
    }

    private String synthesize(CorridorView graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ПРОТОКОЛ СБОРКИ [ГРЯДА -> СТЕРЖЕНЬ -> КРЯЖ] ===\n");
        sb.append(String.format(Locale.US, "Целевые условия: Т=%d | Н=%d | П=%d\n", targetT, targetN, targetP));
        sb.append(String.format(Locale.US, "Порог фильтра КРЯЖ: %.2f\n\n", kryazhMin));

        // 1. Поиск основного узла по тождественности
        Sterzhen mainNode = base.get(0);
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            Sterzhen s = base.get(i);
            int diff = Math.abs(targetT - s.t) + Math.abs(targetN - s.n) + Math.abs(targetP - s.p);
            if (diff < bestDiff) {
                bestDiff = diff;
                mainNode = s;
            }
        }

        List<Sterzhen> assembly = new ArrayList<>();
        assembly.add(mainNode);

        int sumT = mainNode.t, sumN = mainNode.n, sumP = mainNode.p;
        int totalLimit = mainNode.limit;

        sb.append("[1] Основной узел (по тождественности):\n");
        sb.append(String.format(Locale.US, "  * %s [%s]\n    Однородность: %.2f | Равномерность: %.2f\n    Вклад: Т=%d, Н=%d, П=%d | Плотность КРЯЖ: %.2f\n\n",
                mainNode.name, mainNode.cls, mainNode.odnorod, mainNode.ravnomer,
                mainNode.t, mainNode.n, mainNode.p, mainNode.kryazhDensity()));

        sb.append("[2] Добор компенсаторов по аналогии и фильтр КРЯЖ:\n");

        boolean[] used = new boolean[base.size()];
        for (int step = 0; step < 6; step++) {
            int defT = Math.max(0, targetT - sumT);
            int defN = Math.max(0, targetN - sumN);
            int defP = Math.max(0, targetP - sumP);

            if (defT == 0 && defN == 0 && defP == 0) break;

            int bestIdx = -1;
            int bestScore = -1;

            for (int i = 2; i < base.size(); i++) {
                if (used[i]) continue;
                Sterzhen cand = base.get(i);
                int score = (defT > 0 ? cand.t : 0) + (defN > 0 ? cand.n : 0) + (defP > 0 ? cand.p : 0);
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }

            if (bestIdx == -1) break;
            used[bestIdx] = true;
            Sterzhen comp = base.get(bestIdx);
            double density = comp.kryazhDensity();

            if (density >= kryazhMin) {
                assembly.add(comp);
                sumT += comp.t;
                sumN += comp.n;
                sumP += comp.p;
                totalLimit += comp.limit / 2;
                sb.append(String.format(Locale.US, "  + ПРИНЯТ: %s (Плотность=%.2f >= %.2f)\n    Добавлено: Т+%d, Н+%d, П+%d\n",
                        comp.name, density, kryazhMin, comp.t, comp.n, comp.p));
            } else {
                sb.append(String.format(Locale.US, "  - ОТСЕЧЕН КРЯЖЕМ: %s (Плотность=%.2f < %.2f)\n",
                        comp.name, density, kryazhMin));
            }
        }

        int remT = Math.max(0, targetT - sumT);
        int remN = Math.max(0, targetN - sumN);
        int remP = Math.max(0, targetP - sumP);

        int totalTarget = targetT + targetN + targetP;
        int totalSum = sumT + sumN + sumP;
        boolean enough = (remT == 0 && remN == 0 && remP == 0);
        boolean parallelKept = (totalTarget <= totalLimit) && enough;

        graph.parallel = parallelKept;
        graph.loadRatio = totalLimit == 0 ? 100 : (int) ((totalTarget * 72f) / totalLimit);

        sb.append("\n[3] ИТОГОВАЯ СВОДКА КОНТУРА:\n");
        sb.append(String.format(Locale.US, "  Набранная сумма: Т=%d | Н=%d | П=%d (Суммарно: %d)\n", sumT, sumN, sumP, totalSum));
        sb.append(String.format(Locale.US, "  Предел схождения прямых: %d\n", totalLimit));

        if (!enough) {
            sb.append(String.format(Locale.US, "  СТАТУС ГРЯДЫ: Условий недостаточно! Нехватка: Т=%d, Н=%d, П=%d\n", remT, remN, remP));
        }

        if (parallelKept) {
            sb.append("  КОРИДОР СВОЙСТВА: Δ = пост. Параллельность прямых СОХРАНЕНА.\n");
        } else {
            sb.append("  КОРИДОР СВОЙСТВА: Δ -> 0! Нарушение параллельности (выход за предел или нехватка узлов).\n");
        }

        return sb.toString();
    }

    private String readLexicon() {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(getAssets().open("sved.lexicon"), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
        } catch (Exception e) {
            return "Ошибка чтения assets/sved.lexicon: " + e.getMessage();
        }
        return sb.toString();
    }
}
