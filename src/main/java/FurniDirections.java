import com.flagstone.transform.DefineData;
import com.flagstone.transform.Movie;
import com.flagstone.transform.MovieTag;
import gamedata.furnidata.FurniData;
import gamedata.furnidata.furnidetails.FloorItemDetails;
import hotel.Hotel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

public class FurniDirections {
    private static final JSONArray directions = new JSONArray();

    public static void main(String[] args) throws IOException {
        List<FloorItemDetails> floorItems = new FurniData(Hotel.SANDBOX).getAllFloorItems();

        for(FloorItemDetails item : floorItems) {
            Map<String, JSONObject> binaryData = getBinaryDataFromSWF(String.format("https://images.habbo.com/dcr/hof_furni/%d/%s.swf", item.revision, item.className.split("\\*")[0]));
            if(binaryData.containsKey("visualizationData") && binaryData.containsKey("objectData")) {
                directions.put(getDirectionsFromBinaryData(binaryData).put("classname", item.className));
            }
            System.out.println(item.className);
        }

        try (FileWriter file = new FileWriter("directions.json")) {

            file.write(directions.toString());
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

        directions.toList().stream()
                .map(o -> (Map<String, Object>) o)
                .map(JSONObject::new)
                .filter(json -> json.getString("classname").startsWith("wf_"))
                .forEach(System.out::println);
    }

    private static Map<String, JSONObject> getBinaryDataFromSWF(String swfUrl) {
        Movie m = new Movie();
        Map<String, JSONObject> binaryData = new HashMap<>();

        try {
            m.decodeFromUrl(new URL(swfUrl));

            for(MovieTag mt : m.getObjects()) {
                if(mt instanceof DefineData) {
                    DefineData data = (DefineData) mt;
                    JSONObject xml = XML.toJSONObject(new String(data.getData()));
                    binaryData.put(xml.keySet().stream().findFirst().get(), xml);
                }
            }
        } catch (DataFormatException | IOException e) {
            e.printStackTrace();
        }

        return binaryData;
    }

    private static JSONObject getDirectionsFromBinaryData(Map<String, JSONObject> data) {
        try {
            JSONObject visualizationData = data.get("visualizationData");
            JSONObject objectData = data.get("objectData");

            Object directionAnglesObject = objectData
                    .getJSONObject("objectData")
                    .getJSONObject("model")
                    .getJSONObject("directions")
                    .get("direction");

            JSONArray directionAngles;

            if (directionAnglesObject instanceof JSONArray) {
                directionAngles = (JSONArray) directionAnglesObject;
            } else {
                directionAngles = new JSONArray();
                directionAngles.put(directionAnglesObject);
            }

            Object directionIdsObject = visualizationData
                    .getJSONObject("visualizationData")
                    .getJSONObject("graphics")
                    .getJSONArray("visualization")
                    .toList().stream()
                    .map(o -> (Map<String, Object>) o)
                    .map(JSONObject::new)
                    .filter(j -> j.getInt("size") == 64)
                    .findFirst().get()
                    .getJSONObject("directions")
                    .get("direction");

            JSONArray directionIds;
            if (directionIdsObject instanceof JSONArray) {
                directionIds = (JSONArray) directionIdsObject;
            } else {
                directionIds = new JSONArray();
                directionIds.put(directionIdsObject);
            }

            JSONObject dimensions = objectData
                    .getJSONObject("objectData")
                    .getJSONObject("model")
                    .getJSONObject("dimensions");

            JSONArray directions = new JSONArray();

            for (int i = 0; i < directionAngles.length() && i < directionIds.length(); i++) {
                int angle = directionAngles.getJSONObject(i).getInt("id");
                int id = directionIds.getJSONObject(i).getInt("id");

                int x = angle % 180 == 0 ? dimensions.getInt("x") : dimensions.getInt("y");
                int y = angle % 180 == 0 ? dimensions.getInt("y") : dimensions.getInt("x");
                int z = dimensions.getInt("z");

                JSONObject dir = new JSONObject()
                        .put("id", id)
                        .put("angle", angle)
                        .put("x", x)
                        .put("y", y)
                        .put("z", z);

                directions.put(dir);
            }

            return new JSONObject().put("directions", directions);
        } catch (Exception e) {
            return new JSONObject().put("directions", new JSONArray());
        }
    }
}
