package com.btoddb.fastpersitentqueue.chronicle.plunkers;

/*
 * #%L
 * fast-persistent-queue
 * %%
 * Copyright (C) 2014 btoddb.com
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.btoddb.fastpersitentqueue.chronicle.ChronicleMetrics;
import com.btoddb.fastpersitentqueue.chronicle.ChronicleComponentBaseImpl;
import com.btoddb.fastpersitentqueue.chronicle.Config;
import com.btoddb.fastpersitentqueue.chronicle.FpqEvent;
import com.btoddb.fastpersitentqueue.chronicle.FpqPlunker;
import com.codahale.metrics.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;


/**
 *
 */
public abstract class PlunkerBaseImpl extends ChronicleComponentBaseImpl implements FpqPlunker {
    private static final Logger logger = LoggerFactory.getLogger(PlunkerBaseImpl.class);

    protected abstract boolean handleInternal(Collection<FpqEvent> events) throws Exception;


    @Override
    public void init(Config config) throws Exception {
        super.init(config);
    }

    @Override
    public final boolean handle(Collection<FpqEvent> events) throws Exception {
        try {
            config.getMetrics().getRegistry().meter(getId()).mark(events.size());
            return handleInternal(events);
        }
        catch (Exception e) {
            logger.error("exception while handling events in plunker, {}", getId());
        }
        finally {

        }

        return false;
    }
}
