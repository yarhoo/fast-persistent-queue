package com.btoddb.fastpersitentqueue.chronicle;

import com.btoddb.fastpersitentqueue.chronicle.snoopers.Snooper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;


/**
 *
 */
public class RouteAndSnoop {
    private static final Logger logger = LoggerFactory.getLogger(RouteAndSnoop.class);

    private FpqCatcher catcher;
    private Map<String, Snooper> snoopers;

    private Chronicle chronicle;

    public void init(Config config) throws Exception {
        if (null != snoopers) {
            for (Map.Entry<String, Snooper> entry : snoopers.entrySet()) {
                entry.getValue().setId(entry.getKey());
                entry.getValue().init(config);
            }
        }
        else {
            snoopers = Collections.emptyMap();
        }

        // init the catcher last, after all snoopers are ready
        catcher.init(config, this);
    }

    public void shutdown() {
        try {
            catcher.shutdown();
        }
        catch (Exception e) {
            logger.error("exception while shutting down catcher, {}", catcher.getId());
        }

        if (null != snoopers) {
            for (Snooper snooper : snoopers.values()) {
                try {
                    snooper.shutdown();
                }
                catch (Exception e) {
                    logger.error("exception while shutting down snooper, {}", snooper.getId());
                }
            }
        }
    }

    public void handleCatcher(String catcherId, Collection<FpqEvent> eventList) {
        Iterator<FpqEvent> iter = eventList.iterator();
        while (iter.hasNext()) {
            FpqEvent event = iter.next();
            for (Snooper snooper : snoopers.values()) {
                if (!snooper.tap(event)) {
                    // don't want this event anymore, so no more snooping
                    iter.remove();
                    break;
                }
            }
        }
        chronicle.handleCatcher(catcherId, eventList);
    }

    public Chronicle getChronicle() {
        return chronicle;
    }

    public void setChronicle(Chronicle chronicle) {
        this.chronicle = chronicle;
    }

    public FpqCatcher getCatcher() {
        return catcher;
    }

    public void setCatcher(FpqCatcher catcher) {
        this.catcher = catcher;
    }

    public Map<String, Snooper> getSnoopers() {
        return snoopers;
    }

    public void setSnoopers(Map<String, Snooper> snoopers) {
        this.snoopers = snoopers;
    }
}