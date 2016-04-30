package coursesketch.recognition;

import coursesketch.recognition.framework.exceptions.RecognitionException;

/**
 * Created by David Windows on 4/29/2016.
 */
public class RecognitionInitializationException extends RecognitionException {
    /**
     * Creates an exception with a message.
     *
     * @param message The message of the exception.
     */
    public RecognitionInitializationException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message The message of the exception.
     * @param cause The cause of this exception.
     */
    public RecognitionInitializationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
