package com.boxfuse.cloudwatchlogs.log4j2;

import com.boxfuse.cloudwatchlogs.CloudwatchLogsConfig;
import com.boxfuse.cloudwatchlogs.CloudwatchLogsMDCPropertyNames;
import com.boxfuse.cloudwatchlogs.internal.CloudwatchLogsLogEvent;
import com.boxfuse.cloudwatchlogs.internal.CloudwatchLogsLogEventPutter;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.Serializable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Log4J2 appender for Boxfuse's AWS CloudWatch Logs integration.
 */
public class CloudwatchLogsLog4J2Appender extends AbstractAppender {
    private final CloudwatchLogsConfig config = new CloudwatchLogsConfig();
    private final ConcurrentLinkedQueue<CloudwatchLogsLogEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private CloudwatchLogsLogEventPutter putter;

    public CloudwatchLogsLog4J2Appender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    public CloudwatchLogsLog4J2Appender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
    }

    /**
     * @return The config of the appender. This instance can be modified to override defaults.
     */
    public CloudwatchLogsConfig getConfig() {
        return config;
    }

    @Override
    public void start() {
        super.start();
        putter = new CloudwatchLogsLogEventPutter(config, eventQueue);
        new Thread(putter).start();
    }

    @Override
    public void stop() {
        putter.terminate();
        super.stop();
    }

    @Override
    public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        Throwable thrown = event.getThrown();
        while (thrown != null) {
            message += "\n" + dump(thrown);
            thrown = thrown.getCause();
            if (thrown != null) {
                message += "\nCaused by:";
            }
        }

        String account = event.getContextData().getValue(CloudwatchLogsMDCPropertyNames.ACCOUNT);
        String action = event.getContextData().getValue(CloudwatchLogsMDCPropertyNames.ACTION);
        String user = event.getContextData().getValue(CloudwatchLogsMDCPropertyNames.USER);
        String session = event.getContextData().getValue(CloudwatchLogsMDCPropertyNames.SESSION);
        String request = event.getContextData().getValue(CloudwatchLogsMDCPropertyNames.REQUEST);

        Marker marker = event.getMarker();
        String eventId = marker == null ? null : marker.getName();

        eventQueue.add(new CloudwatchLogsLogEvent(event.getLevel().toString(), event.getLoggerName(), eventId, message, event.getTimeMillis(), event.getThreadName(), account, action, user, session, request));
    }

    private String dump(Throwable throwableProxy) {
        StringBuilder builder = new StringBuilder();
        builder.append(throwableProxy.getMessage()).append("\n");
        for (StackTraceElement step : throwableProxy.getStackTrace()) {
            String string = step.toString();
            builder.append("\t").append(string);
            builder.append(step);
            builder.append("\n");
        }
        return builder.toString();
    }
}
