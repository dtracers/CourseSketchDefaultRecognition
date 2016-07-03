package coursesketch.database;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import coursesketch.recognition.framework.TemplateDatabaseInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.srl.sketch.Sketch;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static coursesketch.database.RecognitionStringConstants.INTERPRETATION_LABEL;
import static coursesketch.database.RecognitionStringConstants.OBJECT_TYPE;
import static coursesketch.database.RecognitionStringConstants.SKETCH_DOMAINID;
import static coursesketch.database.RecognitionStringConstants.SKETCH_SKETCH;
import static coursesketch.database.RecognitionStringConstants.TEMPLATE_COLLECTION;
import static coursesketch.database.RecognitionStringConstants.TEMPLATE_DATA;
import static coursesketch.database.RecognitionStringConstants.TEMPLATE_ID;
import static coursesketch.database.RecognitionStringConstants.TEMPLATE_INTERPRETATION;


/**
 * Created by David Windows on 4/13/2016.
 */
public final class RecognitionDatabaseClient implements TemplateDatabaseInterface {

    /**
     * Declaration and Definition of Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(RecognitionDatabaseClient.class);

    /**
     * Converts shapes from Database format to java format and back.
     */
    private final ShapeConverter shapeConverter = new ShapeConverter();

    /**
     * List of database urls.
     */
    private final List<ServerAddress> databaseUrl;

    /**
     * Database names.
     */
    private final String databaseName;

    /**
     * The local database where everything is stored.
     */
    private DB database;

    /**
     * Creates a database interface with the local server information.
     *
     * @param databaseUrl Where the database is located.
     * @param databaseName The name of the database.
     */
    public RecognitionDatabaseClient(final List<ServerAddress> databaseUrl, final String databaseName) {
        this.databaseUrl = databaseUrl;
        this.databaseName = databaseName;
    }

    /**
     * Sets up indexes.
     */
    protected void setUpIndexes() {
        database.getCollection(TEMPLATE_COLLECTION).createIndex(new BasicDBObject(TEMPLATE_INTERPRETATION + '.' + INTERPRETATION_LABEL, 1)
                .append("unique", false));
        database.getCollection(TEMPLATE_COLLECTION).createIndex(new BasicDBObject(TEMPLATE_ID, 1)
                .append("unique", true));
    }

    /**
     * Loads database.
     *
     * @throws UnknownHostException exception
     */
    public void onStartDatabase() throws UnknownHostException {
        final MongoClient mongoClient = new MongoClient(databaseUrl);
        database = mongoClient.getDB(databaseName);
        setUpIndexes();
    }

    @Override
    public void addTemplate(final String templateId, final Sketch.SrlInterpretation srlInterpretation, final Sketch.SrlSketch srlSketch) {
        final DBCollection templates = database.getCollection(TEMPLATE_COLLECTION);

        final String sketchDomainId = srlSketch.getDomainId();

        final List<Object> sketchSketch = new BasicDBList();
        final List<Sketch.SrlObject> sketches = srlSketch.getSketchList();
        for (Sketch.SrlObject sketch : sketches) {
            final DBObject dbSketch = shapeConverter.makeDbObject(sketch);
            sketchSketch.add(dbSketch);
        }

        final BasicDBObject sketchDbObject = new BasicDBObject();
        sketchDbObject.append(SKETCH_DOMAINID, sketchDomainId);
        sketchDbObject.append(SKETCH_SKETCH, sketchSketch);
        final BasicDBObject templateObject = createDefaultTemplate(templateId, srlInterpretation, sketchDbObject);

        // LOG.debug("ADDING TEMPLATE: {}", LoggingConstants.prettyPrintJson(templateObject.toString()));

        templates.insert(templateObject);
    }

    @Override
    public void addTemplate(final String templateId, final Sketch.SrlInterpretation srlInterpretation, final Sketch.SrlShape srlShape) {
        final DBCollection templates = database.getCollection(TEMPLATE_COLLECTION);

        final DBObject shapeDbObject = shapeConverter.makeDbShape(srlShape);

        final BasicDBObject templateObject = createDefaultTemplate(templateId, srlInterpretation, shapeDbObject);
        templateObject.put(OBJECT_TYPE, Sketch.ObjectType.SHAPE.name());

        // LOG.debug("ADDING TEMPLATE: \n\n{}", LoggingConstants.prettyPrintJson(templateObject.toString()));

        templates.insert(templateObject);
    }

    @Override
    public void addTemplate(final String templateId, final Sketch.SrlInterpretation srlInterpretation, final Sketch.SrlStroke srlStroke) {
        final DBCollection templates = database.getCollection(TEMPLATE_COLLECTION);

        final DBObject strokeDbObject = shapeConverter.makeDbStroke(srlStroke);

        final BasicDBObject templateObject = createDefaultTemplate(templateId, srlInterpretation, strokeDbObject);
        templateObject.put(OBJECT_TYPE, Sketch.ObjectType.STROKE.name());

        // LOG.debug("ADDING TEMPLATE: \n\n{}", LoggingConstants.prettyPrintJson(templateObject.toString()));

        templates.insert(templateObject);
    }

    /**
     * Creates the basic template data that is shared across all types of templates.
     *
     * @param templateId The id of the template being stored.
     * @param srlInterpretation The interpretation of the template being stored.
     * @param templateData The data of the template being stored.
     * @return A basic template with some data filled out.
     */
    private BasicDBObject createDefaultTemplate(final String templateId, final Sketch.SrlInterpretation srlInterpretation,
            final DBObject templateData) {
        final BasicDBObject templateObject = new BasicDBObject();

        final DBObject interpretationDbObject = shapeConverter.makeDbInterpretation(srlInterpretation);

        templateObject.append(TEMPLATE_ID, UUID.fromString(templateId));
        templateObject.append(TEMPLATE_INTERPRETATION, interpretationDbObject);
        templateObject.append(TEMPLATE_DATA, templateData);

        return templateObject;
    }

    @Override
    public List<Sketch.RecognitionTemplate> getTemplate(final Sketch.SrlInterpretation srlInterpretation) {
        final List<Sketch.RecognitionTemplate> templateList = new ArrayList<Sketch.RecognitionTemplate>();

        final DBCollection templates = database.getCollection(TEMPLATE_COLLECTION);

        final DBCursor templateObjectCursor = templates.find(
                new BasicDBObject(TEMPLATE_INTERPRETATION + "." + INTERPRETATION_LABEL, srlInterpretation.getLabel()));
        LOG.debug("Number of templates found " + templateObjectCursor.count());

        while (templateObjectCursor.hasNext()) {
            final DBObject templateObject = templateObjectCursor.next();
            templateList.add(shapeConverter.parseRecognitionTemplate(templateObject));
        }
        return templateList;
    }

    @Override
    public List<Sketch.RecognitionTemplate> getAllTemplates() {
        final List<Sketch.RecognitionTemplate> templateList = new ArrayList<Sketch.RecognitionTemplate>();

        final DBCollection templates = database.getCollection(TEMPLATE_COLLECTION);

        final DBCursor templateObjectCursor = templates.find();
        LOG.debug("NUMBER OF TEMPLATES FOUND {}", templateObjectCursor.count());

        while (templateObjectCursor.hasNext()) {
            final DBObject templateObject = templateObjectCursor.next();
            templateList.add(shapeConverter.parseRecognitionTemplate(templateObject));
        }
        return templateList;
    }

    @Override public List<Sketch.SrlInterpretation> getAllInterpretations() {
        final DBCollection templates = database.getCollection(TEMPLATE_COLLECTION);
        final List distinct = templates.distinct("TemplateInterpretation.InterpretationLabel");
        final List<Sketch.SrlInterpretation> interpretations = new ArrayList<>();
        for (Object o : distinct) {
            interpretations.add(Sketch.SrlInterpretation.newBuilder().setLabel("" + o).setConfidence(1).build());
        }
        return interpretations;
    }

}
