package org.droolsassert.util;

import static java.awt.Color.getHSBColor;
import static java.awt.Color.gray;
import static java.awt.Color.white;
import static java.awt.Font.BOLD;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.jfree.chart.ChartFactory.createTimeSeriesChart;
import static org.jfree.chart.ChartUtils.saveChartAsPNG;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public final class ChartUtils {
	
	private ChartUtils() {
	}
	
	public static void pngChart(String fileName, int with, int height, Object... seriesAndColors) {
		try {
			saveChartAsPNG(file(fileName), chart(createTimeSeriesChart(null, null, null, null, false, false, false), seriesAndColors), with, height);
		} catch (IOException e) {
			throw new RuntimeException("Cannot create png chart", e);
		}
	}
	
	public static void pngChart(String fileName, List<TimeSeries> series, int with, int height) {
		try {
			saveChartAsPNG(file(fileName), chart(series, createTimeSeriesChart(null, null, null, null, true, false, false)), with, height);
		} catch (IOException e) {
			throw new RuntimeException("Cannot create png chart", e);
		}
	}
	
	private static File file(String fileName) throws IOException {
		File file = new File(fileName).getAbsoluteFile();
		forceMkdir(file.getParentFile());
		return file;
	}
	
	public static JFreeChart chart(JFreeChart chart, Object... seriesAndColors) {
		XYPlot plot = chart.getXYPlot();
		for (int i = 0; i < seriesAndColors.length; i += 2) {
			plot.setDataset(i / 2, new TimeSeriesCollection((TimeSeries) seriesAndColors[i]));
			XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
			renderer.setSeriesPaint(0, (Color) seriesAndColors[i + 1]);
			plot.setRenderer(i / 2, renderer);
		}
		
		decoratePlot(plot);
		return chart;
	}
	
	public static JFreeChart chart(List<TimeSeries> series, JFreeChart chart) {
		XYPlot plot = chart.getXYPlot();
		int hue = 240; // starting from blue
		
		Color color = null;
		Comparable<?> key = null;
		for (int i = 0; i < series.size(); i++) {
			plot.setDataset(i, new TimeSeriesCollection(series.get(i)));
			
			XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
			if (!series.get(i).getKey().equals(key)) {
				key = series.get(i).getKey();
				color = getHSBColor((hue + 81 * i) % 360f / 360, 1f, 0.5f);
			} else {
				renderer.setDefaultSeriesVisibleInLegend(false);
			}
			renderer.setSeriesPaint(0, color);
			renderer.setLegendTextPaint(0, color);
			renderer.setLegendTextFont(0, new Font("Verdana", BOLD, 11));
			renderer.setLegendLine(new Rectangle(0, 0, 0, 0));
			plot.setRenderer(i, renderer);
		}
		
		chart.getLegend().setBorder(0, 0, 0, 0);
		decoratePlot(plot);
		return chart;
	}
	
	private static void decoratePlot(XYPlot plot) {
		plot.setBackgroundPaint(white);
		plot.setRangeGridlinesVisible(true);
		plot.setRangeGridlinePaint(gray);
		plot.setDomainGridlinesVisible(true);
		plot.setDomainGridlinePaint(gray);
	}
}
