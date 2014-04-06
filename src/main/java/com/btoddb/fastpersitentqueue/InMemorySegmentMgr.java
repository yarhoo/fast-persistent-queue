package com.btoddb.fastpersitentqueue;

import com.eaio.uuid.UUID;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * - has fixed size (in bytes) queue segments
 */
public class InMemorySegmentMgr {
    private long maxSegmentSizeInBytes;
    private int maxNumberOfSegments = 4;
    private AtomicInteger numberOfActiveSegments = new AtomicInteger();

    private ReentrantReadWriteLock segmentsLock = new ReentrantReadWriteLock();
    private LinkedList<MemorySegmentDescriptor> segments = new LinkedList<MemorySegmentDescriptor>();
    private AtomicLong numberOfEntries = new AtomicLong();

    public void init() {
        // TODO:BTB - reload any segment descriptors flushed to disk

        createNewSegment();
    }

    public void push(FpqEntry fpqEntry) {
        push(Collections.singleton(fpqEntry));
    }

    public void push(Collection<FpqEntry> events) {
        // - if enough free size to handle batch, then push events onto current segments
        // - if not, then create new segments and push there
        //   - if too many queues already, then flush newewst one we are not pushing to and load it later
        //

        while (!segments.peekLast().getSegment().push(events)) {
            segmentsLock.writeLock().lock();
            try {
                createNewSegment();
                if (numberOfActiveSegments.get() > maxNumberOfSegments) {
                    // don't serialize the newest because we are "pushing" to it
                        Iterator<MemorySegmentDescriptor> iter = segments.descendingIterator();
                        iter.next(); // get past the newest
                        serializeToDisk(iter.next());
                }
            }
            finally {
                segmentsLock.writeLock().unlock();
            }
        }

        numberOfEntries.addAndGet(events.size());
    }

    private void createNewSegment() {
        UUID newId = new UUID();
        MemorySegment seg = new MemorySegment();
        seg.setId(newId);
        seg.setMaxSizeInBytes(maxSegmentSizeInBytes);
        MemorySegmentDescriptor memDesc = new MemorySegmentDescriptor();
        memDesc.setId(newId);
        memDesc.setStatus(MemorySegmentDescriptor.Status.READY);
        memDesc.setSegment(seg);
        numberOfActiveSegments.incrementAndGet();
        segments.add(memDesc);
    }

    public FpqEntry pop() {
        Collection<FpqEntry> entries = pop(1);
        if (!entries.isEmpty()) {
            return entries.iterator().next();
        }
        else {
            return null;
        }
    }
    public Collection<FpqEntry> pop(int batchSize) {
        // TODO:BTB - manage reading new queue segment from disk if exists

        // - pop at most batchSize events from queue - do not wait to reach batchSize
        //   - if queue empty, do not wait, return empty list immediately

        // find the memory segment we need and reserve our entries
        // will not use multiple segments to achieve 'batchSize'
        MemorySegmentDescriptor chosenSegment = null;
        segmentsLock.readLock().lock();
        try {
            Iterator<MemorySegmentDescriptor> iter = segments.iterator();
            while (iter.hasNext()) {
                MemorySegmentDescriptor seg = iter.next();
                if (MemorySegmentDescriptor.Status.READY != seg.getStatus()) {
                    if (seg.needLoadingTest()) {
                        kickOffLoad(seg);
                    }
                    continue;
                }

                long available = seg.getSegment().getNumberOfAvailableEntries();
                if (0 < available) {
                    chosenSegment = seg;
                    seg.getSegment().decrementAvailable(batchSize <= available ? batchSize : available);
                    break;
                }
            }
        }
        finally {
            segmentsLock.readLock().unlock();
        }

        // if didn't find anything, return null
        if (null == chosenSegment) {
            return null;
        }

        Collection<FpqEntry> entries = chosenSegment.getSegment().pop(batchSize);
        numberOfEntries.addAndGet(-entries.size());

        if (chosenSegment.getSegment().isAvailableForCleanup() && 0 == chosenSegment.getSegment().getNumberOfAvailableEntries()) {
            segmentsLock.writeLock().lock();
            try {
                segments.remove(chosenSegment);
            }
            finally {
                segmentsLock.writeLock().unlock();
            }
        }

        return entries;
    }

    private void serializeToDisk(MemorySegmentDescriptor memDesc) {
        // synchronization should already be done
        numberOfActiveSegments.decrementAndGet();
        memDesc.setStatus(MemorySegmentDescriptor.Status.SAVING);
        memDesc.setStatus(MemorySegmentDescriptor.Status.OFFLINE);
        memDesc.resetNeedLoadingTest();
    }

    private void kickOffLoad(MemorySegmentDescriptor memDesc) {
        // synchronization should already be done
        // TODO:BTB - put this in thread
        memDesc.setStatus(MemorySegmentDescriptor.Status.LOADING);
        memDesc.setStatus(MemorySegmentDescriptor.Status.READY);
        numberOfActiveSegments.incrementAndGet();
        memDesc.resetNeedLoadingTest();
    }

    public long size() {
        return numberOfEntries.get();
    }

    Collection<MemorySegmentDescriptor> getSegments() {
        return segments;
    }

    public long getMaxSegmentSizeInBytes() {
        return maxSegmentSizeInBytes;
    }

    public void setMaxSegmentSizeInBytes(long maxSegmentSizeInBytes) {
        this.maxSegmentSizeInBytes = maxSegmentSizeInBytes;
    }

    public long getNumberOfEntries() {
        return numberOfEntries.get();
    }

    // can't set this, relies on being at least 4
    public int getMaxNumberOfSegments() {
        return maxNumberOfSegments;
    }
}
