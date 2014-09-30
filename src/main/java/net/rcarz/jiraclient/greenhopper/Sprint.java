/**
 * jira-client - a simple JIRA REST client
 * Copyright (c) 2013 Bob Carroll (bob.carroll@alum.rit.edu)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.rcarz.jiraclient.greenhopper;

import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;
import org.joda.time.DateTime;

import static net.rcarz.jiraclient.greenhopper.GreenHopperField.getDateTime;

/**
 * Represents a GreenHopper sprint.
 */
public class Sprint extends GreenHopperResource {

    private enum STATE {
        ACTIVE, CLOSED
    }

    private String name = null;
    private boolean closed = false;
    private DateTime startDate = null;
    private DateTime endDate = null;
    private DateTime completeDate = null;

    /**
     * Creates a sprint from a JSON payload.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     */
    protected Sprint(RestClient restclient, JSONObject json) {
        super(restclient);

        if (json != null)
            deserialise(json);
    }

    private void deserialise(JSONObject json) {
        id = json.getInt("id");
        name = json.getString("name");

        closed = checkClosed(json.optString("state", STATE.CLOSED.name()));
        startDate = getDateTime(json.optString("startDate", null), restclient.getServerLocale());
        endDate = getDateTime(json.optString("endDate", null), restclient.getServerLocale());
        completeDate = getDateTime(json.optString("completeDate", null), restclient.getServerLocale());
    }

    private boolean checkClosed(String state) {
        STATE stateEnum = STATE.valueOf(state);
        return stateEnum != null && stateEnum == STATE.CLOSED;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public Boolean isClosed() {
        return closed;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public DateTime getCompleteDate() {
        return completeDate;
    }
}

