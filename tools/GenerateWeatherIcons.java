import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

public final class GenerateWeatherIcons {
  private static final String[] TYPES = {"clear", "rain", "snow", "mixed", "storm", "fog"};
  private static final int SIZE = 128;
  private static final int MIN_TEMPERATURE = -39;
  private static final int MAX_TEMPERATURE = 39;

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("Usage: GenerateWeatherIcons <background-dir> <output-dir> <AndroidManifest.xml>");
    }

    File backgroundDir = new File(args[0]);
    File outputDir = new File(args[1]);
    File manifestFile = new File(args[2]);
    BufferedImage[] backgrounds = new BufferedImage[TYPES.length];
    for (int i = 0; i < TYPES.length; i++) {
      File file = new File(backgroundDir, "weather_" + TYPES[i] + ".png");
      backgrounds[i] = ImageIO.read(file);
      if (backgrounds[i] == null || backgrounds[i].getWidth() != SIZE || backgrounds[i].getHeight() != SIZE) {
        throw new IllegalArgumentException("Missing or invalid 128x128 background: " + file.getAbsolutePath());
      }
    }

    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new IllegalStateException("Could not create output directory: " + outputDir.getAbsolutePath());
    }
    File[] oldIcons = outputDir.listFiles((dir, name) ->
        name.matches("wx_t(m|p)\\d+_(clear|rain|snow|mixed|storm|fog)\\.png") || name.equals("wx_default.png"));
    if (oldIcons != null) {
      for (File oldIcon : oldIcons) {
        if (!oldIcon.delete()) throw new IllegalStateException("Could not replace " + oldIcon.getAbsolutePath());
      }
    }

    for (int temperature = MIN_TEMPERATURE; temperature <= MAX_TEMPERATURE; temperature++) {
      for (int i = 0; i < TYPES.length; i++) {
        BufferedImage icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        configure(g);
        g.drawImage(backgrounds[i], 0, 0, null);
        drawTemperature(g, temperature);
        g.dispose();
        ImageIO.write(icon, "png", new File(outputDir, iconId(temperature, TYPES[i]) + ".png"));
      }
    }

    writeDefaultIcon(new File(outputDir, "wx_default.png"));
    updateManifestAliases(manifestFile);
    int count = (MAX_TEMPERATURE - MIN_TEMPERATURE + 1) * TYPES.length;
    System.out.println("Generated " + count + " weather icons and the default icon in " + outputDir.getAbsolutePath());
  }

  private static void updateManifestAliases(File manifestFile) throws Exception {
    String manifest = Files.readString(manifestFile.toPath(), StandardCharsets.UTF_8);
    manifest = manifest.replaceAll("(?m)^[ \\t]*<activity-alias android:name=\"\\.Icon_t[mp]\\d+_[a-z]+\"[^\\r\\n]*?</activity-alias>\\r?\\n?", "");
    String defaultAlias = "<activity-alias android:name=\".Icon_default\"";
    int defaultStart = manifest.indexOf(defaultAlias);
    if (defaultStart < 0) throw new IllegalStateException("Could not find .Icon_default in " + manifestFile.getAbsolutePath());
    int insertAt = manifest.indexOf("</activity-alias>", defaultStart);
    if (insertAt < 0) throw new IllegalStateException("Malformed default launcher alias in " + manifestFile.getAbsolutePath());
    insertAt += "</activity-alias>".length();

    StringBuilder aliases = new StringBuilder("\n");
    for (int temperature = MIN_TEMPERATURE; temperature <= MAX_TEMPERATURE; temperature++) {
      String tempPart = temperature < 0 ? "m" + -temperature : "p" + temperature;
      for (String type : TYPES) {
        String resource = iconId(temperature, type);
        aliases.append("        <activity-alias android:name=\".Icon_t").append(tempPart)
            .append('_').append(type).append("\" android:targetActivity=\".MainActivity\" android:enabled=\"false\" android:exported=\"true\" android:icon=\"@mipmap/")
            .append(resource).append("\"><intent-filter><action android:name=\"android.intent.action.MAIN\" /><category android:name=\"android.intent.category.LAUNCHER\" /></intent-filter></activity-alias>\n");
      }
    }

    manifest = manifest.substring(0, insertAt) + aliases + manifest.substring(insertAt);
    Files.writeString(manifestFile.toPath(), manifest, StandardCharsets.UTF_8);
  }

  private static void configure(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }

  private static void drawTemperature(Graphics2D g, int temperature) {
    String label = temperature > 0 ? "+" + temperature : Integer.toString(temperature);
    int digits = Integer.toString(Math.abs(temperature)).length();
    int fontSize = digits == 1 ? 62 : digits == 2 ? 50 : 41;
    g.setFont(new Font("Arial", Font.BOLD, fontSize));
    FontMetrics metrics = g.getFontMetrics();
    float x = (SIZE - metrics.stringWidth(label)) / 2f;

    g.setColor(new Color(12, 35, 65, 155));
    g.drawString(label, x + 1.5f, 83.5f);
    g.setColor(Color.WHITE);
    g.drawString(label, x, 81f);

    int degreeSize = Math.max(7, fontSize / 6);
    int degreeX = Math.round(x + metrics.stringWidth(label)) + Math.max(2, fontSize / 24);
    int degreeY = 81 - Math.round(fontSize * 0.60f);
    g.setStroke(new BasicStroke(Math.max(1.5f, fontSize / 28f)));
    g.drawOval(degreeX, degreeY, degreeSize, degreeSize);
  }

  private static void writeDefaultIcon(File file) throws Exception {
    BufferedImage icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = icon.createGraphics();
    configure(g);
    g.setColor(new Color(0xEAF2FF));
    g.fill(new RoundRectangle2D.Float(3, 3, 122, 122, 33, 33));
    g.setColor(new Color(0xB7CCF5));
    g.setStroke(new BasicStroke(2));
    g.draw(new RoundRectangle2D.Float(4, 4, 120, 120, 31, 31));
    g.setColor(new Color(0x526985));
    drawCloud(g, 41, 52);
    g.dispose();
    ImageIO.write(icon, "png", file);
  }

  private static void drawCloud(Graphics2D g, int x, int y) {
    g.fillOval(x + 2, y + 9, 23, 21);
    g.fillOval(x + 14, y, 25, 29);
    g.fillOval(x + 28, y + 9, 17, 20);
    g.fillRoundRect(x + 3, y + 13, 41, 18, 12, 12);
  }

  private static String iconId(int temperature, String type) {
    return "wx_t" + (temperature < 0 ? "m" + -temperature : "p" + temperature) + "_" + type;
  }
}
