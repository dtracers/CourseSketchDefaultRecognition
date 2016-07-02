package coursesketch.recognition;

import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import coursesketch.recognition.defaults.DefaultRecognition;
import coursesketch.recognition.framework.TemplateDatabaseInterface;
import coursesketch.recognition.framework.exceptions.RecognitionException;
import coursesketch.recognition.framework.exceptions.TemplateException;
import coursesketch.recognition.pdollar.PDollarRecognizer;
import coursesketch.recognition.pdollar.Point;
import coursesketch.recognition.pdollar.RecognizerResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.srl.commands.Commands;
import protobuf.srl.sketch.Sketch;
import protobuf.srl.utils.SketchUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by gigemjt on 4/16/16.
 */
public class BasicRecognition extends DefaultRecognition {
    /**
     * Declaration and Definition of Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(BasicRecognition.class);

    /**
     * The single stroke recognizer.
     */
    private final PDollarRecognizer recognizer = new PDollarRecognizer();

    /**
     * True once recognition have been setup and initialized.
     */
    private boolean initialized = false;

    /**
     * True once recognition have been setup and initialized.
     */
    private boolean trainingComplete = false;

    /**
     * Creates a basic recognition instance.
     *
     * @param templateDatabase The database.
     */
    public BasicRecognition(final TemplateDatabaseInterface templateDatabase) {
        super(templateDatabase);
    }

    /**
     * Initializes the recognizer with the templates.
     */
    public synchronized void initialize() {
        LOG.info("Initializing Basic Recognition");
        if (initialized) {
            return;
        }
        initialized = true;
    }

    @Override public Commands.SrlUpdateList addUpdate(final String s, final Commands.SrlUpdate srlUpdate) throws RecognitionException {
        if (!initialized) {
            initialize();
        }
        return Commands.SrlUpdateList.getDefaultInstance();
    }

    @Override public Commands.SrlUpdateList setUpdateList(final String s, final Commands.SrlUpdateList srlUpdateList) throws RecognitionException {
        if (!initialized) {
            initialize();
        }
        return Commands.SrlUpdateList.getDefaultInstance();
    }

    @Override public Sketch.SrlSketch setSketch(final String s, final Sketch.SrlSketch srlSketch) throws RecognitionException {
        if (!initialized) {
            initialize();
        }
        return null;
    }

    @Override
    public void trainTemplate(final Sketch.RecognitionTemplate template) throws TemplateException {
        final List<Sketch.SrlStroke> strokes = convert(template);
        final List<Point> points = convert(strokes);
        if (points.size() < PDollarRecognizer.mNumPoints / (2 + 2)) {
            LOG.warn("Template was to low in points id:{} interpretation: {}",
                    template.getTemplateId(), template.getInterpretation().getLabel());
            return;
        }
        recognizer.addGesture(template.getInterpretation().getLabel(), points);
    }

    @Override public Commands.SrlUpdateList recognize(final String s, final Commands.SrlUpdateList srlUpdateList) throws RecognitionException {
        if (!initialized) {
            initialize();
        }
        final List<Sketch.SrlStroke> srlStrokes = new ArrayList<>();
        final List<Point> pointCloud = convert(srlUpdateList, srlStrokes);
        final RecognizerResults recognizerResults = recognizer.Recognize(pointCloud);
        LOG.info("RECOGNIZED SKETCH AS " + recognizerResults.mName);
        LOG.info("CONFIDENCE WAS " + recognizerResults.mScore);
        LOG.info("OTHER INFO WAS " + recognizerResults.mOtherInfo);
        final Commands.SrlUpdateList.Builder updateList = Commands.SrlUpdateList.newBuilder();
        final Sketch.SrlInterpretation.Builder interpretation = Sketch.SrlInterpretation.newBuilder();
        interpretation.setLabel(recognizerResults.mName);
        interpretation.setConfidence(recognizerResults.mScore);
        interpretation.setComplexity(1);

        updateList.addList(createUpdateFromResult(interpretation.build(), srlStrokes));

        return updateList.build();
    }

    /**
     * Creates an update from the recognition results.
     *
     * @param interpretation The recognition interpretation.
     * @param affectedStrokes The strokes that were used for this stage of recognition.
     * @return A list of actions that can be used to create the recognized sketch.
     */
    public Commands.SrlUpdate createUpdateFromResult(final Sketch.SrlInterpretation interpretation, final List<Sketch.SrlStroke> affectedStrokes) {
        final Sketch.SrlShape.Builder shape = Sketch.SrlShape.newBuilder();
        shape.setId(UUID.randomUUID().toString());
        shape.setTime(System.currentTimeMillis());
        shape.setIsUserCreated(false);
        shape.addInterpretations(interpretation);
        LOG.debug("CREATING A NEW SHAPE WITH ID: {}", shape.getId());

        final Commands.SrlCommand.Builder addShapeCommand = Commands.SrlCommand.newBuilder();
        addShapeCommand.setCommandType(Commands.CommandType.ADD_SHAPE);
        addShapeCommand.setIsUserCreated(false);
        addShapeCommand.setCommandData(shape.build().toByteString());
        addShapeCommand.setCommandId(UUID.randomUUID().toString());

        final Commands.SrlUpdate.Builder update = Commands.SrlUpdate.newBuilder();

        update.setUpdateId(UUID.randomUUID().toString());
        update.setTime(System.currentTimeMillis());

        final Commands.SrlCommand.Builder packageShape = Commands.SrlCommand.newBuilder();
        packageShape.setIsUserCreated(false);
        packageShape.setCommandId(UUID.randomUUID().toString());
        packageShape.setCommandType(Commands.CommandType.PACKAGE_SHAPE);

        final Commands.ActionPackageShape.Builder actionPackage = Commands.ActionPackageShape.newBuilder();
        final SketchUtil.IdChain.Builder idChain = SketchUtil.IdChain.newBuilder();

        idChain.addIdChain(shape.getId());
        actionPackage.setNewContainerId(idChain);

        for (Sketch.SrlStroke stroke: affectedStrokes) {
            actionPackage.addShapesToBeContained(stroke.getId());
            LOG.debug("PACKING SHAPES WITH ID: {}", stroke.getId());
        }

        packageShape.setCommandData(actionPackage.build().toByteString());

        update.addCommands(addShapeCommand);
        update.addCommands(packageShape);

        return update.build();
    }

    @Override public Sketch.SrlSketch recognize(final String s, final Sketch.SrlSketch srlSketch) throws RecognitionException {
        if (!initialized) {
            initialize();
        }
        return null;
    }

    @Override
    public List<Sketch.SrlInterpretation> recognize(final String s,
            final Sketch.RecognitionTemplate recognitionTemplate) throws RecognitionException {
        final List<Sketch.SrlStroke> strokes = convert(recognitionTemplate);
        final List<Point> points = convert(strokes);
        final RecognizerResults recognizerResults = recognizer.Recognize(points);
        return Lists.newArrayList(createInterpretationFromResult(recognizerResults));
    }

    /**
     * @param recognizerResults Contains recognition data.
     * @return Creates an SrlInpreatation from the results
     */
    private Sketch.SrlInterpretation createInterpretationFromResult(final RecognizerResults recognizerResults) {
        final Sketch.SrlInterpretation.Builder interpretation = Sketch.SrlInterpretation.newBuilder();
        interpretation.setLabel(recognizerResults.mName);
        interpretation.setConfidence(recognizerResults.mScore);
        interpretation.setComplexity(1);
        return interpretation.build();
    }

    @Override public List<Sketch.RecognitionTemplate> generateTemplates(final Sketch.RecognitionTemplate recognitionTemplate) {
        return Lists.newArrayList(recognitionTemplate);
    }

    /**
     * Converts the update list to points for the pdollar algorithm.
     *
     * @param srlUpdateList A list of updates.
     * @param affectedStrokes The list that affected strokes are added to.
     * @return A list of points for the point cloud.
     */
    private List<Point> convert(final Commands.SrlUpdateList srlUpdateList, final List<Sketch.SrlStroke> affectedStrokes) {
        final List<Point> points = new ArrayList<Point>();

        final List<Commands.SrlUpdate> updates = srlUpdateList.getListList();
        for (Commands.SrlUpdate update : updates) {
            final List<Commands.SrlCommand> commands = update.getCommandsList();
            for (Commands.SrlCommand command: commands) {
                final Commands.CommandType commandType = command.getCommandType();
                if (commandType.equals(Commands.CommandType.ADD_STROKE)) {
                    try {
                        final Sketch.SrlStroke stroke = Sketch.SrlStroke.parseFrom(command.getCommandData());
                        final List<Sketch.SrlPoint> srlPoints = stroke.getPointsList();
                        final List<Point> tempPoints = srlPointsToPoint(srlPoints, stroke.getId());
                        points.addAll(tempPoints);
                        affectedStrokes.add(stroke);
                    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                        LOG.error("There was no stroke contained in the request.");
                    }
                }
            }
        }
        return points;
    }

    /**
     * Converts a template into a list of strokes.
     *
     * @param template The sketch template
     * @return A list of strokes
     */
    private List<Sketch.SrlStroke> convert(final Sketch.RecognitionTemplate template) {
        final List<Sketch.SrlStroke> strokes = new ArrayList<Sketch.SrlStroke>();
        if (template.hasStroke()) {
            // LOG.debug("Loading Template {}", template);
            strokes.add(template.getStroke());
        } else if (template.hasShape()) {
            final Sketch.SrlShape shape = template.getShape();
            for (Sketch.SrlObject object: shape.getSubComponentsList()) {
                if (object.getType() == Sketch.ObjectType.STROKE) {
                    try {
                        strokes.add(Sketch.SrlStroke.parseFrom(object.getObject()));
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return strokes;
    }

    /**
     * Converts the list of strokes to a point cloud.
     *
     * @param strokes A list of strokes to be converted.
     * @return A list of points for the point cloud.
     */
    private List<Point> convert(final List<Sketch.SrlStroke> strokes) {
        final List<Point> points = new ArrayList<Point>();
        for (Sketch.SrlStroke stroke : strokes) {
            final List<Sketch.SrlPoint> srlPoints = stroke.getPointsList();
            final List<Point> tempPoints = srlPointsToPoint(srlPoints, stroke.getId());
            points.addAll(tempPoints);
        }
        return points;
    }

    /**
     * Creates a pdollar point from the protobuf point.
     *
     * @param srlPoints The points that are being converted
     * @param strokeId The id of the stroke being added.
     *
     * @return The list of converted points.
     */
    private List<Point> srlPointsToPoint(final List<Sketch.SrlPoint> srlPoints, final String strokeId) {
        final List<Point> points = new ArrayList<Point>();
        for (Sketch.SrlPoint srlPoint : srlPoints) {
            final Point point = new Point(srlPoint.getX(), srlPoint.getY(), strokeId);
            points.add(point);
        }
        return points;
    }

    /**
     * Loads all of the training data into the recognizer.
     *
     * @throws RecognitionInitializationException Thrown if the templates can not be grabbed.
     */
    public synchronized void addAllTrainingData() throws RecognitionInitializationException {
        if (trainingComplete) {
            return;
        }
        List<Sketch.RecognitionTemplate> templates = null;
        try {
            templates = getTemplateDatabase().getAllTemplates();
        } catch (TemplateException e) {
            throw new RecognitionInitializationException("Initialisation failed unable to load all templates", e);
        }

        for (Sketch.RecognitionTemplate template : templates) {
            try {
                trainTemplate(template);
            } catch (TemplateException e) {
                LOG.error("Unable to train template", e);
            }
        }
        trainingComplete = true;
    }
}
