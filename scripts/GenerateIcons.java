import com.textrefinery.ui.components.BrandLogo;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Genera los iconos del instalador (app.ico, app.icns, app.png) a partir del logo dibujado en código.
 * Uso, tras `mvn package`:
 *   java -cp "target/classes;target/libs/*" scripts/GenerateIcons.java            (Windows)
 *   java -cp "target/classes:target/libs/*" scripts/GenerateIcons.java            (macOS / Linux)
 */
public class GenerateIcons {

    public static void main(String[] args) throws IOException {
        Path out = Path.of("src", "main", "resources", "icons");
        Files.createDirectories(out);

        Files.write(out.resolve("app.png"), png(512));
        Files.write(out.resolve("app.ico"), ico(List.of(16, 24, 32, 48, 64, 128, 256)));
        Files.write(out.resolve("app.icns"), icns());
        System.out.println("Iconos generados en " + out.toAbsolutePath());
    }

    private static byte[] png(int size) throws IOException {
        BufferedImage img = BrandLogo.awtImage(size);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** ICO con imágenes PNG incrustadas (soportado desde Windows Vista). */
    private static byte[] ico(List<Integer> sizes) throws IOException {
        List<byte[]> images = new java.util.ArrayList<>();
        for (int s : sizes) {
            images.add(png(s));
        }
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.size()).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.size());
        int offset = 6 + 16 * sizes.size();
        for (int i = 0; i < sizes.size(); i++) {
            int s = sizes.get(i);
            header.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)); // ancho, alto (0 = 256)
            header.put((byte) 0).put((byte) 0);                                   // paleta, reservado
            header.putShort((short) 1).putShort((short) 32);                      // planos, bits por píxel
            header.putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(header.array());
        for (byte[] image : images) {
            bos.write(image);
        }
        return bos.toByteArray();
    }

    /** ICNS con PNG de 128, 256 y 512 px (tipos ic07, ic08 e ic09). */
    private static byte[] icns() throws IOException {
        String[] types = {"ic07", "ic08", "ic09"};
        int[] sizes = {128, 256, 512};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(body);
        for (int i = 0; i < types.length; i++) {
            byte[] data = png(sizes[i]);
            out.writeBytes(types[i]);
            out.writeInt(8 + data.length);
            out.write(data);
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream f = new DataOutputStream(file);
        f.writeBytes("icns");
        f.writeInt(8 + body.size());
        f.write(body.toByteArray());
        return file.toByteArray();
    }
}
