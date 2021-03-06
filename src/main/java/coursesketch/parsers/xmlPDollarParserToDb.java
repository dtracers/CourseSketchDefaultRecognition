package parsers;		
		
import com.mongodb.ServerAddress;		
import coursesketch.database.RecognitionDatabaseClient;		
import coursesketch.server.interfaces.ServerInfo;		
import org.w3c.dom.Document;		
import org.w3c.dom.Element;		
import org.w3c.dom.NamedNodeMap;		
import org.w3c.dom.Node;		
import org.w3c.dom.NodeList;		
import org.xml.sax.SAXException;		
import protobuf.srl.sketch.Sketch;		
		
import javax.xml.parsers.DocumentBuilder;		
import javax.xml.parsers.DocumentBuilderFactory;		
import javax.xml.parsers.ParserConfigurationException;		
import java.io.File;		
import java.io.FileInputStream;		
import java.io.IOException;		
import java.io.InputStream;		
import java.util.ArrayList;		
import java.util.List;		
import java.util.UUID;		
		
/**		
 * Created by gigemjt on 4/16/16.		
 */		
public class XmlPDollarParserToDb {		
    public static void main(String args[]) throws Exception {		
        //Get the DOM Builder Factory		
        System.out.println("Working Directory = " +		
                System.getProperty("user.dir"));		
		
        final List<ServerAddress> databaseUrl = new ArrayList<>();		
        databaseUrl.add(new ServerAddress());		
		
        RecognitionDatabaseClient client = new RecognitionDatabaseClient(new ServerInfo("localhost", 0, 0, false, true, "Recognition", databaseUrl));		
        client.startDatabase();		
		
        File f = new File("../mmg");		
        navigateFiles(f, client);		
        System.out.println(f.getAbsolutePath());		
        // ClassLoader.getSystemResourceAsStream("mmg_example.xml");		
		
        // parseFile(new FileInputStream(new File("mmg_example.xml")), "mmg_example.xml");		
		
    }		
		
    public static void navigateFiles(File folder, RecognitionDatabaseClient client) throws IOException, SAXException, ParserConfigurationException {		
        for (final File fileEntry : folder.listFiles()) {		
            if (fileEntry.isDirectory()) {		
                navigateFiles(fileEntry, client);		
            } else {		
                System.out.println(fileEntry.getAbsolutePath());		
                if (!fileEntry.getName().contains(".xml")) {		
                    System.out.println(fileEntry.getName());		
                    continue;		
                }		
                final InputStream inputStream = new FileInputStream(fileEntry);		
                final Sketch.RecognitionTemplate recognitionTemplate = parseFile(inputStream, fileEntry.getName());		
                client.addTemplate(recognitionTemplate.getInterpretation(), recognitionTemplate.getShape());		
            }		
        }		
    }		
		
    public static Sketch.RecognitionTemplate parseFile(InputStream stream, String fileName) throws ParserConfigurationException, IOException, SAXException {		
        Sketch.RecognitionTemplate.Builder template = Sketch.RecognitionTemplate.newBuilder();		
		
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();		
        //Get the DOM Builder		
        DocumentBuilder builder = factory.newDocumentBuilder();		
		
        Document document =		
                builder.parse(stream);		
        NodeList nodeList = document.getDocumentElement().getChildNodes();		
        final Element documentElement = document.getDocumentElement();		
        String gestureName = documentElement.getAttribute("Name");		
		
        Sketch.SrlShape.Builder shape = Sketch.SrlShape.newBuilder();		
        shape.setTime(0);		
        shape.setId(UUID.randomUUID().toString());		
		
        final NodeList gestureChildNodes = documentElement.getElementsByTagName("Stroke");		
        for (int i = 0; i < gestureChildNodes.getLength(); i++) {		
            final Node stroke = gestureChildNodes.item(i);		
            final Sketch.SrlObject srlObject = parseStroke(stroke);		
            shape.addSubComponents(srlObject);		
        }		
        String realLabel = gestureName.substring(0, gestureName.length() 3);		
        System.out.println("TemplateId: " + fileName);		
        System.out.println("REAL LABEL: " + realLabel);		
		
        // template.setStroke(stroke);		
        template.setInterpretation(Sketch.SrlInterpretation.newBuilder().setConfidence(1).setLabel(realLabel));		
        template.setShape(shape);		
        template.setTemplateId(fileName);		
        return template.build();		
    }		
		
    public static Sketch.SrlObject parseStroke(Node strokeNode) {		
        final Element element = (Element) strokeNode;		
        final Sketch.SrlStroke.Builder srlStroke = parseStroke(element.getElementsByTagName("Point"));		
		
        final NamedNodeMap attributes = element.getAttributes();		
        srlStroke.setId(attributes.getNamedItem("index").getNodeValue());		
		
        Sketch.SrlObject.Builder object = Sketch.SrlObject.newBuilder();		
        object.setType(Sketch.SrlObject.ObjectType.STROKE);		
        object.setObject(srlStroke.build().toByteString());		
        return object.build();		
    }		
		
    public static Sketch.SrlStroke.Builder parseStroke(NodeList gestureChildNodes) {		
        Sketch.SrlStroke.Builder stroke = Sketch.SrlStroke.newBuilder();		
		
        for (int i = 1; i < gestureChildNodes.getLength(); i++) {		
            final Node point = gestureChildNodes.item(i);		
            final NamedNodeMap attributes = point.getAttributes();		
            final Node nodeX = attributes.getNamedItem("X");		
            final Node nodeY = attributes.getNamedItem("Y");		
            final Node nodeTime = attributes.getNamedItem("T");		
            long x = Long.parseLong(nodeX.getNodeValue());		
            long y = Long.parseLong(nodeY.getNodeValue());		
            long time = Long.parseLong(nodeTime.getNodeValue());		
            double pressure = Double.parseDouble(attributes.getNamedItem("Pressure").getNodeValue());		
		
            Sketch.SrlPoint.Builder protoPoint = Sketch.SrlPoint.newBuilder();		
            protoPoint.setId(UUID.randomUUID().toString());		
            protoPoint.setX(x);		
            protoPoint.setY(y);		
            protoPoint.setTime(time);		
            protoPoint.setPressure(pressure);		
            stroke.addPoints(protoPoint);		
        }		
        stroke.setTime(0);		
        return stroke;		
    }		
}