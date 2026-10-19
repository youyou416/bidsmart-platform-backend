package com.bidsmart.platform.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class CaptchaImageRenderer {
    private final SecureRandom random = new SecureRandom();
    public String render(String answer) {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(248, 250, 247)); graphics.fillRect(0, 0, 120, 40);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 10; i++) {
                graphics.setColor(new Color(130 + random.nextInt(100), 130 + random.nextInt(100), 130 + random.nextInt(100)));
                graphics.drawLine(random.nextInt(120), random.nextInt(40), random.nextInt(120), random.nextInt(40));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < answer.length(); i++) {
                graphics.setColor(new Color(20 + random.nextInt(80), 30 + random.nextInt(80), 40 + random.nextInt(80)));
                double angle = (random.nextDouble() - 0.5) * 0.35;
                int x = 9 + i * 27; int y = 29 + random.nextInt(4);
                graphics.rotate(angle, x + 8, 20); graphics.drawString(answer.substring(i, i + 1), x, y);
                graphics.rotate(-angle, x + 8, 20);
            }
            for (int i = 0; i < 70; i++) {
                graphics.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                graphics.fillRect(random.nextInt(120), random.nextInt(40), 1, 1);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes)) { throw new IllegalStateException("PNG encoder unavailable"); }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException error) { throw new IllegalStateException("Captcha rendering unavailable"); }
        finally { graphics.dispose(); }
    }
}
