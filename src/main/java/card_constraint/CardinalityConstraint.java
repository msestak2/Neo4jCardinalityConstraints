package card_constraint;

import com.google.gson.Gson;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.procedure.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CardinalityConstraint {

    public static String relType;

    public static String nodeLabel;

    public static Map subgraph;

    public static Number minKCard;

    public static String maxKCard;

    public static Number k;

    public static Map params;

    public static List<CardinalityConstraint> constraints = new ArrayList<>();

    public CardinalityConstraint() {
    }

    public CardinalityConstraint(CardinalityConstraint c){
        relType = c.getRelType();
        nodeLabel = c.getNodeLabel();
        subgraph = c.getSubgraph();
        minKCard = c.getMinKCard();
        maxKCard = c.getMaxKCard();
        k = c.getK();
        params = c.getParams();
    }

    public CardinalityConstraint(String relType, String nodeLabel, Map subgraph, Number minKCard, String maxKCard, Number k, Map params) {
        CardinalityConstraint.relType = relType;
        CardinalityConstraint.nodeLabel = nodeLabel;
        CardinalityConstraint.subgraph = subgraph;
        CardinalityConstraint.minKCard = minKCard;
        CardinalityConstraint.maxKCard = maxKCard;
        CardinalityConstraint.k = k;
        CardinalityConstraint.params = params;
    }

    @Context
    public GraphDatabaseService db;


    @Procedure(value = "card_constraint.create_card_constraint", mode = Mode.WRITE)
    @Description("create cardinality constraint with props")
    public void createConstraint(@Name("params") Map<String, Object> objectMap) {
        int k = 0;

        Map entry = objectMap;

        while ( entry != null) {
            entry = (Map) entry.get("S");
            if(entry != null)
                k++;
        }

        constraints.add(new CardinalityConstraint(objectMap.get("R").toString(), objectMap.get("E").toString(), (Map)objectMap.get("S"),
                (long) objectMap.get("min"), objectMap.get("max").toString(), k, (Map) objectMap.get("params")));

        objectMap.put("k", k);

        Gson gson = new Gson();
        String jsonSubgraph = gson.toJson(objectMap.get("S"));
        String jsonParams = gson.toJson(objectMap.get("params"));

        String query = "CREATE (c:Card_Constraint {R: \'"+objectMap.get("R") + "\', E: \'" + objectMap.get("E") +
                "\', S: \'" + jsonSubgraph + "\', min : " + objectMap.get("min") + ", max: \'" + objectMap.get("max") +
                "\', k: " + k + ", params: \'" + jsonParams + "\'}) RETURN c";
        System.out.println("Query: " + query);
        db.execute(query);

    }

    public String getRelType() {
        return relType;
    }

    public String getNodeLabel() {
        return nodeLabel;
    }

    public Map getSubgraph() {
        return subgraph;
    }

    public Number getMinKCard() {
        return minKCard;
    }

    public String getMaxKCard() {
        return maxKCard;
    }

    public Number getK() {
        return k;
    }

    public Map getParams() {
        return params;
    }
}
