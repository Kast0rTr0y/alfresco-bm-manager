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
package org.alfresco.bm.manager.api.v1;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.alfresco.bm.common.EventDetails;
import org.alfresco.bm.common.EventResultFilter;
import org.alfresco.bm.common.ResultService;
import org.alfresco.bm.common.ResultService.ResultHandler;
import org.alfresco.bm.common.spring.TestRunServicesCache;
import org.alfresco.bm.common.util.exception.NotFoundException;
import org.alfresco.bm.manager.api.AbstractRestResource;
import org.alfresco.bm.manager.report.CSVReporter;
import org.alfresco.bm.manager.report.XLSXReporter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <b>REST API V1</b><br/>
 * <p>
 * The URL pattern:
 * <ul>
 * <li>&lt;API URL&gt;/v1/tests/{test}/runs/{run}/results</pre></li>
 * </ul>
 * </p>
 * This class presents APIs for retrieving test run results and related information.
 * <p/>
 * It is a meant to be a sub-resource, hence there is no defining path annotation.
 *
 * @author Derek Hulley
 * @since 2.0
 */

@RestController
@RequestMapping(path = "api/v1/tests/{test}/runs/{run}/results")
public class ResultsRestAPI extends AbstractRestResource
{
    /**
     * states not to filter by event names when query event results
     */
    public static final String ALL_EVENT_NAMES = "(All Events)"; 
    
    @Autowired
    private final TestRunServicesCache services;
    
    /**
     * @param services object providing access to necessary test run services
     * @param test     the name of the test
     * @param run      the name of the run
     */
    public ResultsRestAPI(TestRunServicesCache services)
    {
        this.services = services;
    }

    /**
     * @return the {@link ResultService} for the test run
     * @throws WebApplicationException if the service could not be found
     */
    private ResultService getResultService(String test, String run)
    {
        ResultService resultService = services.getResultService(test, run);
        if (resultService == null)
        {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND,
                "Unable to find results for test run " + test + "." + run + ".  Check that the run was configured properly and started.");
        }
        return resultService;
    }
    
    @GetMapping(path="/csv", produces ={"text/csv"})
    public StreamingResponseBody getReportCSV(@PathVariable("test") String test, @PathVariable("run") String run)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Inbound: " + "[test:" + test + ",run:" + run + "]");
        }

        try
        {
            // First confirm that the test exists
            services.getTestService().getTestRunState(test, run);

            // Construct the utility that aggregates the results
            return new StreamingResponseBody()
            {
                @Override
                public void writeTo(OutputStream output) throws IOException
                {
                    CSVReporter csvReporter = new CSVReporter(services, test, run);
                    csvReporter.export(output);
                }
            };
        }
        catch (NotFoundException e)
        {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        catch (HttpClientErrorException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }


    @GetMapping(path = "/xlsx", produces = { "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" })
    public StreamingResponseBody getReportXLSX(@PathVariable("test") String test, @PathVariable("run") String run)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Inbound: " + "[test:" + test + ",run:" + run + "]");
        }

        try
        {
            // First confirm that the test exists
            services.getTestService().getTestRunState(test, run);

            // Construct the utility that aggregates the results
            return new StreamingResponseBody()
            {
                @Override
                public void writeTo(OutputStream output) throws IOException
                {
                    XLSXReporter xlsxReporter = new XLSXReporter(services, test, run);
                    xlsxReporter.export(output);
                }
            };
        }

        catch (HttpClientErrorException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(path = "/eventNames", produces = { "application/json" })
    public String getEventResultEventNames(@PathVariable("test") String test, @PathVariable("run") String run)
    {
        final BasicDBList events = new BasicDBList();

        // always add the "all events" name in the first position
        events.add(ALL_EVENT_NAMES);

        // distinct get all recorded event names from Mongo
        List<String> eventNames = getResultService(test, run).getEventNames();
        for (String eventName : eventNames)
        {
            events.add(eventName);
        }

        return JSON.serialize(events);
    }

    @GetMapping(path = "/allEventsFilterName", produces = { "application/json" })
    public String getAllEventsFilterName()
    {
        final BasicDBList events = new BasicDBList();
        events.add(ALL_EVENT_NAMES);
        return JSON.serialize(events);
    }

    /**
     * Retrieve an approximate number of results, allowing for a smoothing factor
     * (<a href=http://en.wikipedia.org/wiki/Moving_average#Simple_moving_average>Simple Moving Average</a>) -
     * the number of data results to including in the moving average.
     *
     * @param fromTime     the approximate time to start from
     * @param timeUnit     the units of the 'reportPeriod' (default SECONDS).  See {@link TimeUnit}.
     * @param reportPeriod how often a result should be output.  This is expressed as a multiple of the 'timeUnit'.
     * @param smoothing    the number of results to include in the Simple Moving Average calculations
     * @param chartOnly    <tt>true</tt> to filter out results that are not of interest in performance charts
     * @return JSON representing the event start time (x-axis) and the smoothed average execution time
     * along with data such as the events per second, failures per second, etc.
     */
    @GetMapping(path="/ts",produces = {"application/json"})
    public String getTimeSeriesResults(@PathVariable("test") String test, @PathVariable("run") String run, 
            @RequestParam(value= "fromTime",defaultValue="O") long fromTime,
            @RequestParam(value="timeUnit", defaultValue="SECONDS") String timeUnit,
            @RequestParam(value="reportPeriod", defaultValue="1") long reportPeriod,
            @RequestParam(value="smoothing", defaultValue="1") int smoothing,
            @RequestParam(value="chartOnly", defaultValue="true") boolean chartOnly)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(
                "Inbound: " + "[test:" + test + ",fromTime:" + fromTime + ",timeUnit:" + timeUnit + ",reportPeriod:" + reportPeriod + ",smoothing:" + smoothing
                    + ",chartOnly:" + chartOnly + "]");
        }
        if (reportPeriod < 1)
        {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "'reportPeriod' must be 1 or more.");
        }
        if (smoothing < 1)
        {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "'smoothing' must be 1 or more.");
        }
        TimeUnit timeUnitEnum = null;
        try
        {
            timeUnitEnum = TimeUnit.valueOf(timeUnit.toUpperCase());
        }
        catch (HttpClientErrorException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            // Invalid time unit
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        
        final ResultService resultService = getResultService(test, run);

        // Calculate the window size
        long reportPeriodMs = timeUnitEnum.toMillis(reportPeriod);
        long windowSize = reportPeriodMs * smoothing;

        // This is just too convenient an API
        final BasicDBList events = new BasicDBList();
        ResultHandler handler = new ResultHandler()
        {
            @Override
            public boolean processResult(long fromTime, long toTime, Map<String, DescriptiveStatistics> statsByEventName,
                Map<String, Integer> failuresByEventName)
            {
                for (Map.Entry<String, DescriptiveStatistics> entry : statsByEventName.entrySet())
                {
                    String eventName = entry.getKey();
                    DescriptiveStatistics stats = entry.getValue();
                    Integer failures = failuresByEventName.get(eventName);
                    if (failures == null)
                    {
                        logger.error("Found null failure count: " + entry);
                        // Do nothing with it and stop
                        return false;
                    }
                    // Per second
                    double numPerSec = (double) stats.getN() / ((double) (toTime - fromTime) / 1000.0);
                    double failuresPerSec = (double) failures / ((double) (toTime - fromTime) / 1000.0);
                    // Push into an object
                    DBObject eventObj = BasicDBObjectBuilder.start().add("time", toTime).add("name", eventName).add("mean", stats.getMean())
                        .add("min", stats.getMin()).add("max", stats.getMax()).add("stdDev", stats.getStandardDeviation()).add("num", stats.getN())
                        .add("numPerSec", numPerSec).add("fail", failures).add("failPerSec", failuresPerSec).get();
                    // Add the object to the list of events
                    events.add(eventObj);
                }
                // Go for the next result
                return true;
            }
        };
        try
        {
            // Get all the results
            resultService.getResults(handler, fromTime, windowSize, reportPeriodMs, chartOnly);
            // Muster into JSON
            String json = events.toString();

            // Done
            if (logger.isDebugEnabled())
            {
                int jsonLen = json.length();
                if (jsonLen < 500)
                {
                    logger.debug("Outbound: " + json);
                }
                else
                {
                    logger.debug("Outbound: " + json.substring(0, 250) + " ... " + json.substring(jsonLen - 250, jsonLen));
                }
            }
            return json;

        }
        catch (HttpClientErrorException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    
    @GetMapping(path="/eventResults", produces = {"application/json"})
    public String getEventResults(@PathVariable("test") String test, @PathVariable("run") String run, 
            @RequestParam(value="filterEventName", defaultValue=ALL_EVENT_NAMES) String filterEventName,
            @RequestParam(value="filterSuccess", defaultValue="All") String filterSuccess,
            @RequestParam(value="skipResults", defaultValue="0")int skipResults,
            @RequestParam(value="numberOfResults", defaultValue="10") int numberOfResults)
    {

        EventResultFilter filter = getFilter(filterSuccess);
        final ResultService resultService = getResultService(test, run);
        String nameFilterString = filterEventName.equals(ALL_EVENT_NAMES) ? "" : filterEventName;

        // get event details
        List<EventDetails> details = resultService.getEventDetails(filter, nameFilterString, skipResults, numberOfResults);

        // serialize back ....
        BasicDBList retList = new BasicDBList();
        for (EventDetails detail : details)
        {
            retList.add(detail.toDBObject());
        }
        return JSON.serialize(retList);
    }

    /**
     * Returns the enum for a given string or the default value.
     *
     * @param filterEvents (String) one of the string values of EventResultFilter
     * @return (EventResultFilter) - "all" as default, or success / fail
     */
    private EventResultFilter getFilter(String filterEvents)
    {
        try
        {
            return EventResultFilter.valueOf(filterEvents);
        }
        catch (Exception e)
        {
            logger.error("Error converting " + filterEvents + " to EventResultFilter.", e);
        }

        return EventResultFilter.All;
    }

}