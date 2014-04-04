package com.btoddb.fastpersitentqueue;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;


/**
 *
 */
public class BToddBPersistentQueueIT {
    File theDir;
    BToddBPersistentQueue q;

    @Test
    public void testPushNoCommit() throws Exception {
        q.init();

        BToddBContext ctxt = q.createContext();
        q.push(ctxt, new byte[10]);
        q.push(ctxt, new byte[10]);
        q.push(ctxt, new byte[10]);

        assertThat(ctxt.isPushing(), is(true));
        assertThat(ctxt.isPopping(), is(false));
        assertThat(ctxt.getQueue(), hasSize(3));
        assertThat(q.getQueue().size(), is(0L));

        q.commit(ctxt);

        assertThat(ctxt.isPushing(), is(false));
        assertThat(ctxt.isPopping(), is(false));
        assertThat(ctxt.getQueue(), is(nullValue()));
        assertThat(q.getQueue().size(), is(3L));
        assertThat(q.getJournalFileMgr().getCurrentJournalDescriptor().getNumberOfUnconsumedEntries(), is(3L));
    }

    @Test
    public void testPushExceedTxMax() throws Exception {
        q.setMaxTransactionSize(2);
        q.init();

        BToddBContext ctxt = q.createContext();
        q.push(ctxt, new byte[10]);
        q.push(ctxt, new byte[10]);

        try {
            q.push(ctxt, new byte[10]);
            fail("should have thrown exception because of exceeding transaction size");
        }
        catch (BToddBException e) {
            // yay!!
        }

    }

    @Test
    public void testPop() throws Exception {
        q.init();

        BToddBContext ctxt = q.createContext();
        q.push(ctxt, new byte[10]);
        q.push(ctxt, new byte[10]);
        q.push(ctxt, new byte[10]);
        q.commit(ctxt);

        assertThat(q.getQueue().size(), is(3L));

        Collection<Entry> entries = q.pop(ctxt, q.getMaxTransactionSize());

        assertThat(entries, hasSize(3));
        assertThat(ctxt.isPushing(), is(false));
        assertThat(ctxt.isPopping(), is(true));
        assertThat(ctxt.getQueue(), hasSize(3));
        assertThat(q.getQueue().size(), is(0L));
        assertThat(q.getJournalFileMgr().getCurrentJournalDescriptor().getNumberOfUnconsumedEntries(), is(3L));

        q.commit(ctxt);

        assertThat(ctxt.isPushing(), is(false));
        assertThat(ctxt.isPopping(), is(false));
        assertThat(ctxt.getQueue(), is(nullValue()));
        assertThat(q.getQueue().size(), is(0L));
        assertThat(q.getJournalFileMgr().getCurrentJournalDescriptor().getNumberOfUnconsumedEntries(), is(0L));
    }

    // --------------

    @Before
    public void setup() throws IOException {
        theDir = new File("junitTmp_"+ UUID.randomUUID().toString());
        FileUtils.forceMkdir(theDir);

        q = new BToddBPersistentQueue();
        q.setMaxTransactionSize(100);
        q.setJournalDirectory(theDir);
    }

    @After
    public void cleanup() throws IOException {
        q.shutdown();
        FileUtils.deleteDirectory(theDir);
    }

}
