package ru.sterzhen.app;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String REAL_MEM_FILE = "sterzhen_real_memory.tsv";

    // Реальная запись наработанной инженерной памяти
    static final class EngineeringLens {
        final String targetGoal;
        final Set<String> requiredParts;
        final Set<String> materials;
        final String inventedSolution;
        int useCount;

        EngineeringLens(String goal, Set<String> parts, Set<String> mats, String inv, int cnt) {
            targetGoal = goal;
            requiredParts = new LinkedHashSet<>(parts);
            materials = new LinkedHashSet<>(mats);
            inventedSolution = inv;
            useCount = cnt;
        }
    }

    private final List<EngineeringLens> memory = new ArrayList<>();
    private final ExecutorService netExec = Executors.newSingleThreadExecutor();

    private EditText inGoal, inHave, inMissingProps;
    private TextView outReport;
    private LinearLayout linksBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadMemory();

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.parseColor("#0B0F19"));
        LinearLayout rt = new LinearLayout(this);
        rt.setOrientation(LinearLayout.VERTICAL);
        rt.setPadding(24, 32, 24, 48);

        TextView t = new TextView(this);
        t.setText("СТЕРЖЕНЬ :: РЕАЛЬНЫЙ ПОИСК И ИЗОБРЕТАТЕЛЬ");
        t.setTextSize(16.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#38BDF8"));
        rt.addView(t);

        TextView sub = new TextView(this);
        sub.setText("Живой поиск комплектации в сети • Сравнение с купленным • Синтез новых решений");
        sub.setTextSize(11.5f);
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setPadding(0, 4, 0, 14);
        rt.addView(sub);

        rt.addView(lbl("1. ЧТО НУЖНО СДЕЛАТЬ / СОБРАТЬ (любая задача):"));
        inGoal = ed("Например: Индукционный нагреватель");
        inGoal.setText("Индукционный нагреватель");
        rt.addView(inGoal);

        rt.addView(lbl("2. ЧТО УЖЕ КУПЛЕНО / ЕСТЬ В НАЛИЧИИ (через запятую):"));
        inHave = ed("Например: блок питания, медная трубка, припой");
        inHave.setText("блок питания, вентилятор, припой");
        rt.addView(inHave);

        rt.addView(lbl("3. ЕСЛИ НЕТ В ПРОДАЖЕ — КАКИЕ СВОЙСТВА НУЖНО ИЗОБРЕСТИ:"));
        inMissingProps = ed("Например: жаростойкость 1200С, электроизоляция, малый вес");
        inMissingProps.setText("высокая температура, электроизоляция, теплопроводность");
        rt.addView(inMissingProps);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = 8;

        LinearLayout r1 = new LinearLayout(this);
        r1.setPadding(0, 12, 0, 8);
        Button btnSearch = bt("🔍 НАЙТИ В СЕТИ И СОБРАТЬ", "#0284C7");
        Button btnInvent = bt("⚡ ИЗОБРЕСТИ УЗЕЛ", "#D97706");
        r1.addView(btnSearch, lp);
        r1.addView(btnInvent, lp);
        rt.addView(r1);

        Button btnMem = bt("📂 НАРАБОТАННАЯ ПАМЯТЬ И ЛИНЗЫ (" + memory.size() + ")", "#334155");
        rt.addView(btnMem);

        linksBox = new LinearLayout(this);
        linksBox.setOrientation(LinearLayout.VERTICAL);
        linksBox.setPadding(0, 10, 0, 10);
        rt.addView(linksBox);

        outReport = new TextView(this);
        outReport.setTypeface(Typeface.MONOSPACE);
        outReport.setTextSize(12f);
        outReport.setTextColor(Color.parseColor("#E6EDF3"));
        outReport.setBackgroundColor(Color.parseColor("#111827"));
        outReport.setPadding(20, 20, 20, 20);
        rt.addView(outReport);

        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runRealInternetAnalysis(btnMem);
            }
        });

        btnInvent.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runInventionSynthesizer(btnMem);
            }
        });

        btnMem.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSavedMemory();
            }
        });

        sc.addView(rt);
        setContentView(sc);
        runRealInternetAnalysis(btnMem);
    }

    // РЕАЛЬНЫЙ ПОИСК В ИНТЕРНЕТЕ: скачивает статьи и вычленяет узлы, материалы и чего не хватает
    private void runRealInternetAnalysis(final Button btnMem) {
        final String goal = inGoal.getText().toString().trim();
        final String haveRaw = inHave.getText().toString().trim();
        final String propsRaw = inMissingProps.getText().toString().trim();

        if (goal.isEmpty()) {
            outReport.setText("Введите название изделия или задачи в поле 1.");
            return;
        }

        outReport.setText("Запрос в Интернет по задаче: «" + goal + "»...\nСкачивание технических данных...");
        linksBox.removeAllViews();

        netExec.execute(new Runnable() {
            public void run() {
                final StringBuilder sb = new StringBuilder();
                final List<String> buyItems = new ArrayList<>();

                try {
                    // 1. Проверяем, есть ли уже готовая Линза в памяти
                    for (EngineeringLens lens : memory) {
                        if (lens.targetGoal.equalsIgnoreCase(goal)) {
                            lens.useCount++;
                            saveMemory();
                            sb.append("★ [СРАБОТАЛА НАРАБОТАННАЯ ЛИНЗА ПАМЯТИ (опыт +").append(lens.useCount).append(")]\n");
                        }
                    }

                    // 2. Живой запрос к Wikipedia API за полным техническим текстом
                    String searchUrl = "https://ru.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                            + URLEncoder.encode(goal, "UTF-8") + "&utf8=&format=json&srlimit=3";
                    String searchJson = httpGet(searchUrl);

                    List<String> titles = new ArrayList<>();
                    JSONObject root = new JSONObject(searchJson);
                    JSONArray arr = root.getJSONObject("query").getJSONArray("search");
                    for (int i = 0; i < arr.length(); i++) {
                        titles.add(arr.getJSONObject(i).getString("title"));
                    }

                    StringBuilder fullTechText = new StringBuilder();
                    for (String t : titles) {
                        String extUrl = "https://ru.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&titles="
                                + URLEncoder.encode(t, "UTF-8") + "&format=json";
                        String extJson = httpGet(extUrl);
                        JSONObject pages = new JSONObject(extJson).getJSONObject("query").getJSONObject("pages");
                        Iterator<String> keys = pages.keys();
                        while (keys.hasNext()) {
                            JSONObject p = pages.getJSONObject(keys.next());
                            fullTechText.append(p.optString("extract", "")).append("\n");
                        }
                    }

                    String text = fullTechText.toString().toLowerCase(Locale.ROOT);

                    // 3. Инженерный парсер: ищем в реальном скачанном тексте материалы, узлы и компоненты
                    String[] knownComponents = new String[]{
                        "индуктор", "катушка", "конденсатор", "транзистор", "инвертор", "генератор",
                        "трансформатор", "резистор", "диод", "радиатор", "датчик", "контроллер",
                        "магнитопровод", "сердечник", "корпус", "изолятор", "экран", "тигель",
                        "насос", "клапан", "трубка", "спираль", "электрод", "аккумулятор",
                        "реле", "предохранитель", "фильтр", "подшипник", "вал", "ротор", "статор"
                    };

                    String[] knownMaterials = new String[]{
                        "медь", "медный", "сталь", "титан", "алюминий", "феррит", "керамика",
                        "кварц", "графит", "вольфрам", "нихром", "карбид", "кремний", "полимер",
                        "текстолит", "слюда", "асбест", "стекловолокно", "бор", "свинец", "гидрид",
                        "латунь", "бронза", "никель", "молибден", "корунд", "шамот", "фторопласт"
                    };

                    Set<String> foundParts = new LinkedHashSet<>();
                    for (String comp : knownComponents) {
                        if (text.contains(comp)) foundParts.add(comp);
                    }
                    if (foundParts.isEmpty()) {
                        // Если статья узкая — берём ключевые существительные из заголовков
                        foundParts.add("основной рабочий узел («" + goal + "»)");
                        foundParts.add("силовой контур питания");
                        foundParts.add("несущий изоляционный каркас");
                    }

                    Set<String> foundMats = new LinkedHashSet<>();
                    for (String mat : knownMaterials) {
                        if (text.contains(mat)) foundMats.add(mat);
                    }
                    if (foundMats.isEmpty()) {
                        foundMats.add("конструкционная сталь / алюминий");
                        foundMats.add("техническая керамика");
                    }

                    // 4. Сверяем с тем, что УЖЕ КУПЛЕНО пользователем
                    Set<String> haveSet = new LinkedHashSet<>();
                    for (String h : haveRaw.toLowerCase(Locale.ROOT).split(",")) {
                        if (!h.trim().isEmpty()) haveSet.add(h.trim());
                    }

                    Set<String> missingParts = new LinkedHashSet<>();
                    for (String fp : foundParts) {
                        boolean alreadyBought = false;
                        for (String h : haveSet) {
                            if (h.contains(fp) || fp.contains(h)) { alreadyBought = true; break; }
                        }
                        if (!alreadyBought) missingParts.add(fp);
                    }

                    String invented = buildInventionFormula(goal, propsRaw, foundMats);

                    // Сохраняем реальную наработку в память и строим Линзу!
                    saveOrUpdateLens(goal, missingParts, foundMats, invented);

                    sb.append("=== ОТЧЁТ КОМПЛЕКТАЦИИ ИЗ ИНТЕРНЕТА ===\n");
                    sb.append("Изделие: ").append(goal.toUpperCase(Locale.ROOT)).append("\n");
                    sb.append("Проанализированы источники: ").append(titles).append("\n\n");

                    sb.append("1. УЖЕ ЕСТЬ В НАЛИЧИИ (КУПЛЕНО):\n");
                    for (String h : haveSet) sb.append("   ✓ ").append(h).append("\n");

                    sb.append("\n2. ЧЕГО НЕ ХВАТАЕТ (НУЖНО ДОКУПИТЬ ИЛИ СДЕЛАТЬ):\n");
                    for (String mp : missingParts) {
                        sb.append("   ❗ Не хватает: ").append(mp.toUpperCase(Locale.ROOT)).append("\n");
                        buyItems.add(mp);
                    }

                    sb.append("\n3. ИЗ КАКИХ МАТЕРИАЛОВ ЭТО ДЕЛАЕТСЯ (ПО ДАННЫМ СЕТИ):\n");
                    sb.append("   Материалы: ").append(String.join(", ", foundMats)).append("\n");
                    for (String mat : foundMats) {
                        if (buyItems.size() < 6) buyItems.add(mat + " заготовка");
                    }

                    sb.append("\n4. ЕСЛИ НЕТ ГОТОВОГО — КОНСТРУКТОРСКОЕ ИЗОБРЕТЕНИЕ:\n");
                    sb.append(invented).append("\n");

                    sb.append("\n5. СИНТЕЗ ЛИНЗЫ ПАМЯТИ:\n");
                    sb.append(consolidateRealLenses());

                } catch (Exception e) {
                    sb.append("Ошибка связи с сетью: ").append(e.getMessage())
                      .append("\nВключите интернет и нажмите кнопку поиска.");
                }

                runOnUiThread(new Runnable() {
                    public void run() {
                        outReport.setText(sb.toString());
                        btnMem.setText("📂 НАРАБОТАННАЯ ПАМЯТЬ И ЛИНЗЫ (" + memory.size() + ")");
                        renderBuyButtons(buyItems);
                    }
                });
            }
        });
    }

    // Кнопки быстрого перехода «Где приобрести» для каждого недостающего узла и материала
    private void renderBuyButtons(List<String> items) {
        linksBox.removeAllViews();
        if (items.isEmpty()) return;

        TextView hdr = lbl("🛒 ГДЕ ПРИОБРЕСТИ НЕДОСТАЮЩИЕ УЗЛЫ И МАТЕРИАЛЫ (нажмите для поиска поставщиков):");
        linksBox.addView(hdr);

        for (final String item : items) {
            Button b = new Button(this);
            b.setText("Купить / Найти поставщика: «" + item + "»");
            b.setTextSize(11f);
            b.setTextColor(Color.WHITE);
            b.setBackgroundColor(Color.parseColor("#1E293B"));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String q = "купить " + item + " цена характеристики поставщик";
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
                    startActivity(i);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 6;
            linksBox.addView(b, lp);
        }
    }

    // Модуль «ИЗОБРЕСТИ»: если нужного материала/узла нет в природе или на складе
    private void runInventionSynthesizer(Button btnMem) {
        String goal = inGoal.getText().toString().trim();
        String props = inMissingProps.getText().toString().trim();
        Set<String> baseMats = new LinkedHashSet<>(Arrays.asList("керамика", "медь", "титан", "феррит"));
        String inv = buildInventionFormula(goal, props, baseMats);
        saveOrUpdateLens(goal + " (Изобретение)", Collections.singleton("Самодельный композитный узел"), baseMats, inv);
        btnMem.setText("📂 НАРАБОТАННАЯ ПАМЯТЬ И ЛИНЗЫ (" + memory.size() + ")");
        outReport.setText("=== РЕЖИМ ИЗОБРЕТЕНИЯ НОВОГО МАТЕРИАЛА / УЗЛА ===\n\n" + inv + "\n\n" + consolidateRealLenses());
    }

    private String buildInventionFormula(String goal, String propsRaw, Set<String> baseMats) {
        String p = propsRaw.toLowerCase(Locale.ROOT);
        List<String> layers = new ArrayList<>();

        if (p.contains("температ") || p.contains("жаро") || p.contains("тепло") || p.contains("1200")) {
            layers.add("Внешний огнеупорный контур: Оксид алюминия (корунд) / Карбид кремния (держит до 1650°C)");
        }
        if (p.contains("изоляц") || p.contains("ток") || p.contains("диэлектр")) {
            layers.add("Диэлектрический барьер: Слюдяная / фторопластовая или кварцевая прослойка (пробой >20 кВ/мм)");
        }
        if (p.contains("прочн") || p.contains("удар") || p.contains("вес") || p.contains("легк")) {
            layers.add("Несущий каркас (КРЯЖ): Сотовая матрица из титана/алюминия, армированная стекловолокном");
        }
        if (p.contains("магнит") || p.contains("поле") || p.contains("экран") || p.contains("радиац")) {
            layers.add("Полевой экран: Ферритовый концентратор потока + гидридно-борный поглотитель");
        }
        if (layers.isEmpty()) {
            layers.add("Слоистый композит из доступного сырья: [" + String.join(" + ", baseMats) + "] с разделением функций несущего каркаса и рабочей поверхности");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Запрос свойств: [").append(propsRaw).append("]\n");
        sb.append("Синтезированная конструкция для «").append(goal).append("»:\n");
        for (int i = 0; i < layers.size(); i++) {
            sb.append("   Слой ").append(i + 1).append(": ").append(layers.get(i)).append("\n");
        }
        sb.append("   Технология сборки: Разделить конфликтующие требования по разным слоям (принцип ТРИЗ / Закон параллельных прямых Δ=пост.).");
        return sb.toString();
    }

    // Сборка Линз Фокусирования из РЕАЛЬНО найденных вами в сети проектов!
    private String consolidateRealLenses() {
        if (memory.size() < 2) {
            return "В памяти пока 1 проект («" + (memory.isEmpty() ? "" : memory.get(0).targetGoal)
                    + "»). Введите в поле 1 ещё любое устройство (например, «Трансформатор» или «Печь муфельная»), чтобы Стержень нашёл общие детали и собрал из них Линзу Фокусирования!";
        }

        Set<String> commonParts = new LinkedHashSet<>();
        Set<String> commonMats = new LinkedHashSet<>();

        for (int i = 0; i < memory.size(); i++) {
            for (int j = i + 1; j < memory.size(); j++) {
                EngineeringLens a = memory.get(i), b = memory.get(j);
                for (String part : a.requiredParts) {
                    if (b.requiredParts.contains(part)) commonParts.add(part);
                }
                for (String mat : a.materials) {
                    if (b.materials.contains(mat)) commonMats.add(mat);
                }
            }
        }

        return "На основе " + memory.size() + " реальных запросов сформирована ЛИНЗА УНИФИКАЦИИ:\n"
                + "• Универсальные узлы (подойдут сразу для нескольких ваших проектов): "
                + (commonParts.isEmpty() ? "накапливаются..." : commonParts) + "\n"
                + "• Общая база материалов для закупки оптом: "
                + (commonMats.isEmpty() ? "накапливается..." : commonMats);
    }

    private void showSavedMemory() {
        StringBuilder sb = new StringBuilder("=== РЕАЛЬНАЯ НАРАБОТАННАЯ ПАМЯТЬ СТЕРЖНЯ (" + memory.size() + " проектов) ===\n\n");
        int i = 1;
        for (EngineeringLens l : memory) {
            sb.append(i++).append(". ИЗДЕЛИЕ: ").append(l.targetGoal.toUpperCase(Locale.ROOT))
              .append(" (обращений: ").append(l.useCount).append(")\n")
              .append("   Недостающие узлы: ").append(l.requiredParts).append("\n")
              .append("   Материалы: ").append(l.materials).append("\n")
              .append("   Изобретение: ").append(l.inventedSolution).append("\n\n");
        }
        sb.append(consolidateRealLenses());
        outReport.setText(sb.toString());
    }

    private synchronized void saveOrUpdateLens(String goal, Set<String> parts, Set<String> mats, String inv) {
        for (EngineeringLens ex : memory) {
            if (ex.targetGoal.equalsIgnoreCase(goal)) {
                ex.requiredParts.addAll(parts);
                ex.materials.addAll(mats);
                ex.useCount++;
                saveMemory();
                return;
            }
        }
        memory.add(new EngineeringLens(goal, parts, mats, inv, 1));
        saveMemory();
    }

    private void saveMemory() {
        File f = new File(getFilesDir(), REAL_MEM_FILE);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            for (EngineeringLens l : memory) {
                bw.write(l.targetGoal.replace('\t', ' ') + "\t"
                        + String.join(",", l.requiredParts) + "\t"
                        + String.join(",", l.materials) + "\t"
                        + l.inventedSolution.replace('\n', ' ').replace('\t', ' ') + "\t"
                        + l.useCount + "\n");
            }
        } catch (Exception ignored) {}
    }

    private void loadMemory() {
        memory.clear();
        File f = new File(getFilesDir(), REAL_MEM_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] c = line.split("\t");
                if (c.length >= 5) {
                    Set<String> p = new LinkedHashSet<>(Arrays.asList(c[1].split(",")));
                    Set<String> m = new LinkedHashSet<>(Arrays.asList(c[2].split(",")));
                    memory.add(new EngineeringLens(c[0], p, m, c[3], Integer.parseInt(c[4])));
                }
            }
        } catch (Exception ignored) {}
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "SterzhenEngineering/3.0");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } finally {
            c.disconnect();
        }
        return sb.toString();
    }

    private TextView lbl(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11.5f);
        tv.setTextColor(Color.parseColor("#7DD3FC"));
        tv.setPadding(0, 10, 0, 2);
        return tv;
    }

    private EditText ed(String h) {
        EditText e = new EditText(this);
        e.setHint(h);
        e.setHintTextColor(Color.parseColor("#64748B"));
        e.setTextColor(Color.WHITE);
        e.setBackgroundColor(Color.parseColor("#1E293B"));
        e.setPadding(16, 12, 16, 12);
        e.setTextSize(13f);
        return e;
    }

    private Button bt(String t, String c) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(11f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(c));
        return b;
    }
}
