import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders the TV banner (320x180) and the Play store icon (512x512). Run: java tools/RenderBrandAssets.java */
public class RenderBrandAssets {
    static final Color INK = new Color(0x0A0C0F), PAPER = new Color(0xF6F1E8), GOLD = new Color(0xD9B97F);

    public static void main(String[] args) throws Exception {
        Font display = Font.createFont(Font.TRUETYPE_FONT, new File("tv/src/main/res/font/cormorant_garamond.ttf"));
        Font ui = Font.createFont(Font.TRUETYPE_FONT, new File("tv/src/main/res/font/inter.ttf"));
        banner(display, ui);
        icon();
        // Play Console store-listing graphics.
        wide(display, ui, 1280, 720, "store-listing/tv-banner-1280x720.png");
        wide(display, ui, 1024, 500, "store-listing/feature-graphic-1024x500.png");
    }

    static void wide(Font display, Font ui, int w, int h, String out) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(img);
        background(g, w, h);
        double u = h / 180.0; // scale of the 320x180 banner layout
        palace(g, 10 * u, 14 * u, 64 * u, GOLD);
        g.setColor(PAPER);
        g.setFont(display.deriveFont(Font.PLAIN, (float) (36 * u)));
        g.drawString("Guest", (int) (24 * u), (int) (112 * u));
        g.drawString("Experience", (int) (24 * u), (int) (146 * u));
        int tvX = (int) (24 * u) + g.getFontMetrics().stringWidth("Experience") + (int) (10 * u);
        g.setColor(GOLD);
        g.setFont(ui.deriveFont(Font.BOLD, (float) (13 * u)));
        g.drawString("T V", tvX, (int) (146 * u));
        g.dispose();
        ImageIO.write(img, "png", new File(out));
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** Palace mark in a 108-unit box, like the adaptive icon. */
    static void palace(Graphics2D g, double x, double y, double s, Color c) {
        AffineTransform old = g.getTransform();
        g.translate(x, y); g.scale(s / 108.0, s / 108.0);
        g.setColor(c);
        g.fill(new Rectangle2D.Double(30, 62, 48, 4));
        g.fill(new Rectangle2D.Double(36, 50, 36, 12));
        g.fill(new Rectangle2D.Double(47, 42, 14, 8));
        g.fill(new Arc2D.Double(40, 46, 8, 8, 0, 180, Arc2D.CHORD));
        g.fill(new Arc2D.Double(60, 46, 8, 8, 0, 180, Arc2D.CHORD));
        g.fill(new Arc2D.Double(48, 36, 12, 12, 0, 180, Arc2D.CHORD));
        g.fill(new Rectangle2D.Double(53.5, 32, 1, 4));
        g.setColor(INK);
        g.fill(new Rectangle2D.Double(41, 54, 3, 5)); g.fill(new Rectangle2D.Double(52.5, 54, 3, 5)); g.fill(new Rectangle2D.Double(64, 54, 3, 5));
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 102));
        g.fill(new Rectangle2D.Double(34, 70, 40, 1.5)); g.fill(new Rectangle2D.Double(40, 74, 28, 1.2)); g.fill(new Rectangle2D.Double(46, 78, 16, 1));
        g.setTransform(old);
    }

    static void background(Graphics2D g, int w, int h) {
        g.setColor(INK); g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.2, h * 0.25), Math.max(w, h) * 0.75f,
            new float[]{0f, 1f}, new Color[]{new Color(0x221D14), INK}));
        g.fillRect(0, 0, w, h);
    }

    static void banner(Font display, Font ui) throws Exception {
        int w = 320, h = 180;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(img);
        background(g, w, h);
        palace(g, 10, 14, 64, GOLD);
        g.setColor(PAPER);
        g.setFont(display.deriveFont(Font.PLAIN, 36f));
        g.drawString("Guest", 24, 112);
        g.drawString("Experience", 24, 146);
        int tvX = 24 + g.getFontMetrics().stringWidth("Experience") + 10;
        g.setColor(GOLD);
        g.setFont(ui.deriveFont(Font.BOLD, 13f));
        g.drawString("T V", tvX, 146);
        g.dispose();
        ImageIO.write(img, "png", new File("tv/src/main/res/drawable-xhdpi/banner.png"));
    }

    static void icon() throws Exception {
        int s = 512;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(img);
        background(g, s, s);
        palace(g, 0, 0, s, GOLD);
        g.dispose();
        ImageIO.write(img, "png", new File("store-listing/icon-512.png"));
    }
}
