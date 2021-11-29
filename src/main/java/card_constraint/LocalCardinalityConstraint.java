package card_constraint;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LocalCardinalityConstraint {

    public long _id;

    @SerializedName("N1")
    public JsonElement node1;

    @SerializedName("E1")
    public String edge1;

    @SerializedName("N2")
    public JsonElement node2;

    public int min;

    public String max;

    public String constraintPattern;

    public String fullPattern;

    public  List<String> criteria = new ArrayList<>();

    public LocalCardinalityConstraint() {
    }


    public LocalCardinalityConstraint(long _id, JsonElement node1, String edge1, JsonElement node2, int min, String max, String constraintPattern, String fullPattern, List<String> criteria) {
        this._id = _id;
        this.node1 = node1;
        this.edge1 = edge1;
        this.node2 = node2;
        this.min = min;
        this.max = max;
        this.constraintPattern = constraintPattern;
        this.fullPattern = fullPattern;
        this.criteria = criteria;
    }
}
