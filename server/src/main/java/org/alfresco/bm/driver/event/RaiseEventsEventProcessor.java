/*
 * #%L
 * Alfresco Benchmark Manager
 * %%
 * Copyright (C) 2005 - 2018 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.bm.driver.event;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.alfresco.bm.common.EventResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits a given number of events at a given frequency for a given time.
 * <p/> 
 * Note that derivations of this class can be used to generate the desired
 * data for each of the raised events.
 * 
 * <h1>Input</h1>
 * 
 * Any data
 * 
 * <h1>Actions</h1>
 * 
 * Emits events in batches before rescheduling itself.
 * 
 * <h1>Output</h1>
 * 
 * {@link #setEventNameRaiseEvents(String)}: Self-triggering event<br/>
 * {@link #outputEventName}: Obtains data from {@link #getNextEventData()}<br/>
 * 
 * @author Derek Hulley
 * @since 1.4
 */
public class RaiseEventsEventProcessor extends AbstractEventProcessor
{
    private static final String ERR_INCORRECT_INBOUND_TYPE = "The event processor takes no initial input.";
    private static final String MSG_CREATED_EVENTS = "Scheduled %3d events named %s.";
    
    public static final String KEY_OUTPUT_EVENTS_RAISED = "outputEventsRaised";
    public static final String KEY_LAST_EVENT_TIME = "lastEventTime";
    
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final String DEFAULT_EVENT_NAME_RAISE_EVENTS = "raiseEvents";
    
    private final String outputEventName;
    private final long timeBetweenEvents;
    private final int outputEventCount;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private String eventNameRaiseEvents = DEFAULT_EVENT_NAME_RAISE_EVENTS;
    
    /**
     * Constructor with <b>essential</b> values
     * 
     * @param outputEventName               the name of the event to emit
     * @param timeBetweenEvents             the time between events
     * @param outputEventCount              the number of events to emit
     */
    public RaiseEventsEventProcessor(
            String outputEventName,
            long timeBetweenEvents,
            int outputEventCount)
    {
        super();
        this.outputEventName = outputEventName;
        this.timeBetweenEvents = timeBetweenEvents;
        this.outputEventCount = outputEventCount;
    }

    /**
     * Override the {@link #DEFAULT_BATCH_SIZE default} batch size
     */
    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_RAISE_EVENTS default} name for repeat batches
     */
    public void setEventNameRaiseEvents(String eventNameRaiseEvents)
    {
        this.eventNameRaiseEvents = eventNameRaiseEvents;
    }

    @Override
    public EventResult processEvent(Event event)
    {
        // Get the previous data associated with this processor
        DBObject data;
        try
        {
            data = (DBObject) event.getData();
        }
        catch (ClassCastException e)
        {
            EventResult result = new EventResult(ERR_INCORRECT_INBOUND_TYPE, false);
            return result;
        }
        if (data == null)
        {
            data = new BasicDBObject()
                    .append(KEY_LAST_EVENT_TIME, Long.valueOf(System.currentTimeMillis()))
                    .append(KEY_OUTPUT_EVENTS_RAISED, Integer.valueOf(0));
        }
        int localTotal = 0;
        int total = (Integer) data.get(KEY_OUTPUT_EVENTS_RAISED);
        long time = (Long) data.get(KEY_LAST_EVENT_TIME);
        List<Event> nextEvents = new ArrayList<Event>(outputEventCount);
        // Keep going as long as there is capacity and we are under the batch size
        while (total < outputEventCount && localTotal < batchSize)
        {
            localTotal++;
            total++;
            time += timeBetweenEvents;
            // Raise another event
            Object nextEventData = getNextEventData();
            Event nextEvent = new Event(outputEventName, time, nextEventData);
            nextEvents.add(nextEvent);
        }
        // Reschedule ourself, if necessary
        if (total < outputEventCount)
        {
            // Still more to do
            data = new BasicDBObject()
                    .append(KEY_LAST_EVENT_TIME, time)
                    .append(KEY_OUTPUT_EVENTS_RAISED, total);
            Event nextEvent = new Event(eventNameRaiseEvents, time, data);
            nextEvents.add(nextEvent);
        }
        // Done
        return new EventResult(
                String.format(MSG_CREATED_EVENTS, localTotal, outputEventName),
                nextEvents,
                true);
    }
    
    /**
     * Get data to provide for each event raised.  Implementations can override to
     * add any data that is required for the each of the events being raised.
     * <p/>
     * The default is to return <tt>null</tt> i.e. no data will be provided to each new event.
     * <p/>
     * Use a {@link DBObject} for complicated data transfer
     * 
     * @return              Return data to attach to the generated events
     */
    protected Object getNextEventData()
    {
        // No data
        return null;
    }
}
