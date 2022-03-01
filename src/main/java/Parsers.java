import com.flagstone.transform.DoABC;
import com.flagstone.transform.Movie;
import com.flagstone.transform.MovieTag;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

public class Parsers {
    public static void main(String[] args) throws DataFormatException, IOException {
        findParsers();
    }

    public static void findParsers() throws DataFormatException, IOException {
        Movie m = new Movie();
        m.decodeFromFile(new File("C:\\Users\\jonas\\Downloads\\HabboAir.swf"));

        m.getObjects()
                .stream()
                .filter(mt -> mt instanceof DoABC)
                .map(mt -> (DoABC) mt)
                .forEach(doABC -> {
                    System.out.println(doABC);
                });
    }
}
