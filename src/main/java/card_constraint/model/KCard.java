package card_constraint.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class KCard {
    @SerializedName("N1")
    private JsonElement node1;
    @SerializedName("E1")
    private String edge1;
    @SerializedName("N2")
    private JsonElement node2;
    private int min;
    private String max;

    public KCard(JsonElement node1, String edge1, JsonElement node2, int min, String max) {
        this.node1 = node1;
        this.edge1 = edge1;
        this.node2 = node2;
        this.min = min;
        this.max = max;
    }

    public JsonElement getNode1() {
        return node1;
    }

    public void setNode1(JsonElement node1) {
        this.node1 = node1;
    }

    public String getEdge1() {
        return edge1;
    }

    public void setEdge1(String edge1) {
        this.edge1 = edge1;
    }

    public JsonElement getNode2() {
        return node2;
    }

    public void setNode2(JsonElement node2) {
        this.node2 = node2;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public String getMax() {
        return max;
    }

    public void setMax(String max) {
        this.max = max;
    }

}
