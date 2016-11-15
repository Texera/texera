package edu.uci.ics.textdb.web.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class defines a link in the operator graph
 * Created by kishorenarendran on 10/21/16.
 */
public class OperatorLinkBean {
    @JsonProperty("from")
    private String fromOperatorID;
    @JsonProperty("to")
    private String toOperatorID;

    public OperatorLinkBean() {
    }

    public OperatorLinkBean(String fromOperatorID, String toOperatorID) {
        this.fromOperatorID = fromOperatorID;
        this.toOperatorID = toOperatorID;
    }

    public String getFromOperatorID() {
        return fromOperatorID;
    }

    public void setFromOperatorID(String fromOperatorID) {
        this.fromOperatorID = fromOperatorID;
    }

    public String getToOperatorID() {
        return toOperatorID;
    }

    public void setToOperatorID(String toOperatorID) {
        this.toOperatorID = toOperatorID;
    }
}
