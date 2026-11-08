import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class GenerateWeatherIcons {
  private static final String[] TYPES = {"clear", "rain", "snow", "mixed", "storm", "fog"};
  private static final int SIZE = 128;
  private static final int MIN_TEMPERATURE = -39;
  private static final int MAX_TEMPERATURE = 39;

  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      throw new IllegalArgumentException("Usage: GenerateWeatherIcons <background-dir> <mipmap-dir> <drawable-dir> <AndroidManifest.xml>");
    }

    File backgroundDir = new File(args[0]);
    File mipmapDir = new File(args[1]);
    File drawableDir = new File(args[2]);
    File manifestFile = new File(args[3]);
    BufferedImage[] backgrounds = new BufferedImage[TYPES.length];
    for (int i = 0; i < TYPES.length; i++) {
      File source = new File(backgroundDir, "weather_" + TYPES[i] + ".png");
      backgrounds[i] = ImageIO.read(source);
      if (backgrounds[i] == null || backgrounds[i].getWidth() != SIZE || backgrounds[i].getHeight() != SIZE) {
        throw new IllegalArgumentException("Missing or invalid 128x128 background: " + source.getAbsolutePath());
      }
    }

    makeDirectory(mipmapDir);
    makeDirectory(drawableDir);
    deleteGeneratedFiles(mipmapDir, "wx_t(m|p)\\d+_(clear|rain|snow|mixed|storm|fog)\\.png|wx_default\\.png");
    deleteGeneratedFiles(drawableDir, "wx_t(m|p)\\d+_(clear|rain|snow|mixed|storm|fog)\\.xml|weather_(clear|rain|snow|mixed|storm|fog)\\.png");

    for (int i = 0; i < TYPES.length; i++) {
      File destination = new File(drawableDir, "weather_" + TYPES[i] + ".png");
      Files.copy(new File(backgroundDir, "weather_" + TYPES[i] + ".png").toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    for (int temperature = MIN_TEMPERATURE; temperature <= MAX_TEMPERATURE; temperature++) {
      for (String type : TYPES) {
        writeLayeredIcon(new File(drawableDir, iconId(temperature, type) + ".xml"), temperature, type);
      }
    }

    writeDefaultIcon(new File(mipmapDir, "wx_default.png"));
    updateManifestAliases(manifestFile);
    int count = (MAX_TEMPERATURE - MIN_TEMPERATURE + 1) * TYPES.length;
    System.out.println("Generated " + count + " vector-overlay icons, six shared backgrounds and the default icon.");
  }

  private static void makeDirectory(File directory) {
    if (!directory.exists() && !directory.mkdirs()) {
      throw new IllegalStateException("Could not create output directory: " + directory.getAbsolutePath());
    }
  }

  private static void deleteGeneratedFiles(File directory, String regex) {
    File[] oldFiles = directory.listFiles((dir, name) -> name.matches(regex));
    if (oldFiles != null) {
      for (File oldFile : oldFiles) {
        if (!oldFile.delete()) throw new IllegalStateException("Could not replace " + oldFile.getAbsolutePath());
      }
    }
  }

  private static void writeLayeredIcon(File file, int temperature, String type) throws Exception {
    String label = temperature > 0 ? "+" + temperature : Integer.toString(temperature);
    int digits = Integer.toString(Math.abs(temperature)).length();
    int fontSize = digits == 1 ? 62 : digits == 2 ? 50 : 41;

    BufferedImage measureImage = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = measureImage.createGraphics();
    configure(g);
    Font font = new Font("Arial", Font.BOLD, fontSize);
    g.setFont(font);
    FontMetrics metrics = g.getFontMetrics();
    float x = (SIZE - metrics.stringWidth(label)) / 2f;
    GlyphVector glyphs = font.createGlyphVector(g.getFontRenderContext(), label);
    Shape text = glyphs.getOutline(x, 81f);
    Shape shadow = AffineTransform.getTranslateInstance(1.5, 2.5).createTransformedShape(text);

    int degreeSize = Math.max(7, fontSize / 6);
    int degreeX = Math.round(x + metrics.stringWidth(label)) + Math.max(2, fontSize / 24);
    int degreeY = 81 - Math.round(fontSize * 0.60f);
    float degreeStroke = Math.max(1.5f, fontSize / 28f);
    Shape degree = new Ellipse2D.Float(degreeX, degreeY, degreeSize, degreeSize);
    g.dispose();

    String vector = "        <vector android:width=\"128dp\" android:height=\"128dp\" android:viewportWidth=\"128\" android:viewportHeight=\"128\">\n"
        + "            <path android:fillColor=\"#9B0C2341\" android:pathData=\"" + pathData(shadow) + "\" />\n"
        + "            <path android:fillColor=\"#FFFFFFFF\" android:pathData=\"" + pathData(text) + "\" />\n"
        + "            <path android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"" + format(degreeStroke) + "\" android:pathData=\"" + pathData(degree) + "\" />\n"
        + "        </vector>";
    String xml = "<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
        + "    <item android:drawable=\"@drawable/weather_" + type + "\" />\n"
        + "    <item>\n" + vector + "\n    </item>\n"
        + "</layer-list>\n";
    Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);
  }

  private static String pathData(Shape shape) {
    StringBuilder data = new StringBuilder();
    PathIterator iterator = shape.getPathIterator(null);
    float[] p = new float[6];
    while (!iterator.isDone()) {
      int segment = iterator.currentSegment(p);
      if (data.length() > 0) data.append(' ');
      switch (segment) {
        case PathIterator.SEG_MOVETO:
          data.append('M').append(format(p[0])).append(',').append(format(p[1]));
          break;
        case PathIterator.SEG_LINETO:
          data.append('L').append(format(p[0])).append(',').append(format(p[1]));
          break;
        case PathIterator.SEG_QUADTO:
          data.append('Q').append(format(p[0])).append(',').append(format(p[1])).append(' ')
              .append(format(p[2])).append(',').append(format(p[3]));
          break;
        case PathIterator.SEG_CUBICTO:
          data.append('C').append(format(p[0])).append(',').append(format(p[1])).append(' ')
              .append(format(p[2])).append(',').append(format(p[3])).append(' ')
              .append(format(p[4])).append(',').append(format(p[5]));
          break;
        case PathIterator.SEG_CLOSE:
          data.append('Z');
          break;
        default:
          throw new IllegalStateException("Unsupported vector path segment: " + segment);
      }
      iterator.next();
    }
    return data.toString();
  }

  private static String format(float number) {
    return String.format(Locale.US, "%.2f", number);
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
        aliases.append("        <activity-alias android:name=\".Icon_t").append(tempPart)
            .append('_').append(type).append("\" android:targetActivity=\".MainActivity\" android:enabled=\"false\" android:exported=\"true\" android:icon=\"@drawable/")
            .append(iconId(temperature, type)).append("\"><intent-filter><action android:name=\"android.intent.action.MAIN\" /><category android:name=\"android.intent.category.LAUNCHER\" /></intent-filter></activity-alias>\n");
      }
    }

    manifest = manifest.substring(0, insertAt) + aliases + manifest.substring(insertAt);
    Files.writeString(manifestFile.toPath(), manifest, StandardCharsets.UTF_8);
  }

  private static void configure(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
    g.fillOval(43, 61, 23, 21);
    g.fillOval(55, 52, 25, 29);
    g.fillOval(69, 61, 17, 20);
    g.fillRoundRect(44, 65, 41, 18, 12, 12);
    g.dispose();
    ImageIO.write(icon, "png", file);
  }

  private static String iconId(int temperature, String type) {
    return "wx_t" + (temperature < 0 ? "m" + -temperature : "p" + temperature) + "_" + type;
  }
}
