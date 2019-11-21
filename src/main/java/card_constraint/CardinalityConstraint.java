package card_constraint;

import com.google.gson.Gson;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.api.proc.Neo4jTypes;
import org.neo4j.procedure.*;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import javax.smartcardio.Card;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CardinalityConstraint {

    public static String relType;

    public static String nodeLabel;

    public static Map subgraph;

    public static Number minKCard;

    public static Number maxKCard;

    public static Number k;

    public static List<CardinalityConstraint> constraints = new ArrayList<>();

    public CardinalityConstraint() {
    }

    public CardinalityConstraint(CardinalityConstraint c){
        this.relType = c.getRelType();
        this.nodeLabel = c.getNodeLabel();
        this.subgraph = c.getSubgraph();
        this.minKCard = c.getMinKCard();
        this.maxKCard = c.getMaxKCard();
        this.k = c.getK();
    }

    public CardinalityConstraint(String relType, String nodeLabel, Map subgraph, Number minKCard, Number maxKCard, Number k) {
        this.relType = relType;
        this.nodeLabel = nodeLabel;
        this.subgraph = subgraph;
        this.minKCard = minKCard;
        this.maxKCard = maxKCard;
        this.k = k;
    }

    @Context
    public GraphDatabaseService db;


    @Procedure(value = "card_constraint.create_card_constraint", mode = Mode.WRITE)
    @Description("create cardinality constraint")
    public void createConstraint(@Name("params") Map<String, Object> objectMap) {
        int k = 0;

        Map entry = objectMap;

        while ( entry != null) {
            System.out.println("E: " + entry.get("E"));
            entry = (Map) entry.get("S");
            if(entry != null)
                k++;
        }

        System.out.println("K: " + k);
        constraints.add(new CardinalityConstraint(objectMap.get("R").toString(), objectMap.get("E").toString(), (Map)objectMap.get("S"),
                (long)objectMap.get("min"), (long)objectMap.get("max"), k));
        System.out.println("List K size: " + constraints.size());

        objectMap.put("k", k);
        System.out.println("ObjectMap: " + objectMap);

        Gson gson = new Gson();
        String jsonSubgraph = gson.toJson(objectMap.get("S"));

        String query = "CREATE (c:Card_Constraint {R: \'"+objectMap.get("R") + "\', E: \'" + objectMap.get("E") +
                "\', S: \'" + jsonSubgraph + "\', min : " + (long)objectMap.get("min") + ", max: " + (long)objectMap.get("max") +
                ", k: " + k + "}) RETURN c";
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

    public Number getMaxKCard() {
        return maxKCard;
    }

    public Number getK() {
        return k;
    }

}
