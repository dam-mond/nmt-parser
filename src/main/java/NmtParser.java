import org.apache.commons.io.IOUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import picocli.CommandLine;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CommandLine.Command(name = "nmt-parser", mixinStandardHelpOptions = true,
        description = "Script used to plot a chart with values collected from NMT files")
public class NmtParser implements Callable<Integer> {

    @CommandLine.Option(names = { "--zipFile" }, required = true, description = "Zip file containing the NMT files")
    private String zipFile;

    @CommandLine.Option(names = { "--excludeHeap" }, required = false, defaultValue = "false", description = "Exclude Java heap from the chart, useful to improve readability")
    private boolean excludeHeap;

    @Override
    public Integer call() throws IOException {
        Pattern pattern = Pattern.compile("- (.*) \\(.* committed=(\\d*)KB");
        Path filePath = Path.of(zipFile);
        System.out.println("Filepath: " + zipFile);
        if(!Files.exists(filePath)){
            throw new IllegalArgumentException("File " + zipFile + "not found!");
        }
        URI zipUri = filePath.toUri();
        Map<Long, Map<String, String>> dateFileMap = new TreeMap<>();
        File f = new File(zipUri);

        ZipFile zipFile = new ZipFile(f);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            if(!entry.getName().contains("__MACOSX") && entry.getName().contains("diff")){
                long fileModifiedMillis = entry.getLastModifiedTime().toMillis();
                Map<String, String> valuesPerFile = new HashMap<>();
                try (InputStream in = zipFile.getInputStream(entry)) {
                    String contents = IOUtils.toString(in, StandardCharsets.UTF_8);
                    Matcher matcher = pattern.matcher(contents);
                    while (matcher.find()) {
                        String sectionName = matcher.group(1);
                        String committedMemoryKB = matcher.group(2);
                        if(sectionName != null && !sectionName.trim().isEmpty()){
                            valuesPerFile.put(sectionName.trim(), committedMemoryKB);
                        }
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                dateFileMap.put(fileModifiedMillis, valuesPerFile);
            }
        }
        System.out.println(dateFileMap);
        createChartFile(dateFileMap);
        return 0;
    }

    private void createChartFile(Map<Long, Map<String, String>> map) throws IOException {
        Map<String, XYSeries> plotLineMap = new HashMap<>();
        for (Map.Entry<Long, Map<String, String>> longMapEntry : map.entrySet()) {
            Map<String, String> regexMap = longMapEntry.getValue();
            for (Map.Entry<String, String> regexEntry : regexMap.entrySet()) {
                String memorySection = regexEntry.getKey();
                if(excludeHeap){
                    if(memorySection.equalsIgnoreCase("java heap")){
                        continue;
                    }
                }

                String memorySectionAmountKb = regexEntry.getValue();

                XYSeries series;
                boolean seriesAlreadyExists = plotLineMap.containsKey(memorySection);
                if(seriesAlreadyExists){
                    series = plotLineMap.get(memorySection);
                }else{
                    series = new XYSeries(memorySection);
                }

                double x = Double.parseDouble(longMapEntry.getKey().toString());
                double y = Double.parseDouble(memorySectionAmountKb);
                series.add(x, y);
                plotLineMap.put(memorySection, series);
            }
        }

        var dataset = new XYSeriesCollection();
        for (XYSeries series : plotLineMap.values()) {
            dataset.addSeries(series);
        }

        JFreeChart chart = createChart(dataset, "NMT memory behaviour", "Time", "Memory (Kb)");
        ChartUtils.saveChartAsJPEG(new File("nmtChart.png"), chart, 2000, 1000);
        System.out.println("Chart generated!");
    }

    private static JFreeChart createChart(XYDataset dataset, String chartName, String xLabel, String yLabel) {
        JFreeChart chart = ChartFactory.createXYLineChart(
                chartName,
                xLabel,
                yLabel,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();

        var renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));

        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.white);

        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.BLACK);

        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(Color.BLACK);

        chart.getLegend().setFrame(BlockBorder.NONE);
        return chart;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new NmtParser()).execute(args);
        System.exit(exitCode);
    }

}
