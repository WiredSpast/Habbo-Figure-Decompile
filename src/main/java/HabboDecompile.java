import com.flagstone.transform.*;
import com.flagstone.transform.image.DefineImage2;
import javafx.util.Pair;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.XML;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterInputStream;

public class HabboDecompile {
    public static String productionUrl;

    public static void main(String[] args) throws IOException {
        productionUrl = getLatestProductionUrl();

        JSONObject figureMap = getFigureMap();
        List<String> figureNames = getFigureNames(figureMap);

        System.out.println(figureNames.size());

        for (int i = 0; i < 10; i ++) {
            extractImagesFromSwf(figureNames.get(i));
        }
    }

    public static List<String> getFigureNames(JSONObject figureMap) {
        return figureMap.getJSONObject("map")
                        .getJSONArray("lib")
                        .toList().stream()
                        .map(o -> (HashMap<String, Object>) o)
                        .map(m -> (String) m.get("id"))
                        .collect(Collectors.toList());
    }

    public static String getLatestProductionUrl() throws IOException {
        String page = IOUtils.toString(new URL("https://sandbox.habbo.com/gamedata/external_variables/1").openStream(), StandardCharsets.UTF_8);
        for (String s : page.split("\n")) {
            if(s.startsWith("flash.client.url")) {
                return "https:" + s.replace("flash.client.url=", "");
            }
        }
        return page;
    }

    public static JSONObject getFigureMap() throws IOException {
        return XML.toJSONObject(IOUtils.toString(new URL(productionUrl + "figuremapv2.xml").openStream(), StandardCharsets.UTF_8));
    }

    public static void extractImagesFromSwf(String swfName) {
        Movie m = new Movie();
        try {
            m.decodeFromUrl(new URL(productionUrl + swfName + ".swf"));
            Map<String, Integer> imgIds = new HashMap<>();
            Map<Integer, BufferedImage> images = new HashMap<>();
            List<String> order = new ArrayList<>();
            Map<String, Pair<Integer, Integer>> positions = new HashMap<>();

            for(MovieTag mt : m.getObjects()) {
                if(mt instanceof SymbolClass) {
                    SymbolClass symbolClass = (SymbolClass) mt;
                    symbolClass.getObjects().forEach((key, value) -> {
                        imgIds.put(value.replace(swfName + "_", ""), key);
                    });
                }

                if(mt instanceof DefineData) {
                    DefineData defineData = (DefineData) mt;
                    JSONObject xml = XML.toJSONObject(new String(defineData.getData()));
                    System.out.println(xml.toString(2));
                    xml.getJSONObject("manifest")
                            .getJSONObject("library")
                            .getJSONObject("assets")
                            .getJSONArray("asset")
                            .toList().stream()
                            .map(o -> (HashMap<String, Object>) o)
                            .forEach(asset -> {
                                String coords = (String) ((HashMap<String, Object>) asset.get("param")).get("value");
                                int x = Integer.parseInt(coords.split(",")[0]);
                                int y = Integer.parseInt(coords.split(",")[1]);
                                String name = (String) asset.get("name");
                                order.add(name);
                                positions.put(name, new Pair<>(x, y));
                            });
                }

                if(mt instanceof DefineImage2) {
                    DefineImage2 defineImage = (DefineImage2) mt;
                    BufferedImage bufImg = new BufferedImage(defineImage.getWidth(), defineImage.getHeight(), BufferedImage.TRANSLUCENT);
                    Graphics2D graphics = bufImg.createGraphics();
                    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));

                    ByteArrayInputStream bais = new ByteArrayInputStream(defineImage.getImage());
                    InflaterInputStream iis = new InflaterInputStream(bais);

                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();

                    byte[] buf = new byte[10];
                    while (true) {
                        int size = iis.read(buf);
                        if (size <= 0)
                            break;
                        outStream.write(buf, 0, size);
                    }
                    outStream.close();

                    byte[] bytes = outStream.toByteArray();

                    int width = defineImage.getWidth();
                    int height = defineImage.getHeight();

                    for(int i = 0; i < width; i++) {
                        for(int j = 0; j < height; j++) {
                            int byteIndex = (j * width + i) * 4;
                            int a = bytes[byteIndex] & 0xFF;
                            int r = bytes[byteIndex + 1] & 0xFF;
                            int g = bytes[byteIndex + 2] & 0xFF;
                            int b = bytes[byteIndex + 3] & 0xFF;
                            graphics.setColor(new Color(r, g, b, a));
                            System.out.println(bytes[byteIndex]);
                            System.out.println(bytes[byteIndex] & 0xFF);
                            System.out.println(((float) (bytes[byteIndex] & 0xFF))/255);
                            graphics.drawRect(i, j, 0, 0);
                        }
                    }
                    images.put(defineImage.getIdentifier(), bufImg);
                }
            }

            if(!Files.isDirectory(Paths.get(swfName))) {
                Files.createDirectory(Paths.get(swfName));
            }

            int xMin = 500;
            int yMin = 500;
            int xMax = -500;
            int yMax = -500;

            for(String name : order) {
                BufferedImage image = images.get(imgIds.get(name));
                if(image != null) {
                    if (name.contains("std") && name.contains("2_0")) {
                        int x = -positions.get(name).getKey();
                        int y = -positions.get(name).getValue();
                        if(x < xMin) xMin = x;
                        if(y < yMin) yMin = y;
                        if(x + image.getWidth() > xMax) xMax = x + image.getWidth();
                        if(y + image.getHeight() > yMax) yMax = y + image.getHeight();
                    }
                }
            }

            BufferedImage img = new BufferedImage(xMax-xMin, yMax - yMin, Color.TRANSLUCENT);
            Graphics2D graphics = img.createGraphics();
            graphics.translate(-xMin, -yMin);

            for(String name : order) {
                BufferedImage image = images.get(imgIds.get(name));
                if(image != null) {
                    if (name.contains("std") && name.contains("2_0")) {
                        System.out.println(name);
                        graphics.drawImage(image, -positions.get(name).getKey(), -positions.get(name).getValue(), image.getWidth(), image.getHeight(), null);
                    }
                    ImageIO.write(image, "png", new File(swfName + "/" + name + ".png"));
                }
            }


            ImageIO.write(img, "png", new File(swfName + "/" + swfName + ".png"));

        } catch (DataFormatException | IOException e) {
            e.printStackTrace();
        }
    }
}
