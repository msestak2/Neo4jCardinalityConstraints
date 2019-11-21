package card_constraint;

import com.google.gson.Gson;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.procedure.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LocalCardinalityConstraint {

    public long _id;

    public String relType;

    public String nodeLabel;

    public  Map subgraph;

    public  Number minKCard;

    public  Number maxKCard;

    public  Number k;

    public  List<LocalCardinalityConstraint> constraints = new ArrayList<>();

    public LocalCardinalityConstraint() {
    }


    public LocalCardinalityConstraint(long id, String relType, String nodeLabel, Map subgraph, Number minKCard, Number maxKCard, Number k) {
        this._id = id;
        this.relType = relType;
        this.nodeLabel = nodeLabel;
        this.subgraph = subgraph;
        this.minKCard = minKCard;
        this.maxKCard = maxKCard;
        this.k = k;
    }

}
