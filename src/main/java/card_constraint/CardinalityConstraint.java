package card_constraint;

import card_constraint.model.KCard;
import com.google.gson.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.procedure.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CardinalityConstraint {

    public static JsonElement node1;
    public static String edge1;
    public static JsonElement node2;
    public static int min;
    public static String max;
    public static String constraintPattern;
    public static String fullPattern;
    public static List<String> criteria;
    public static List<CardinalityConstraint> constraints = new ArrayList<>();

    public CardinalityConstraint() {
    }

    public CardinalityConstraint(CardinalityConstraint c) {
        node1 = c.getNode1();
        edge1 = c.getEdge1();
        node2 = c.getNode2();
        min = c.getMin();
        max = c.getMax();
        constraintPattern = c.getConstraintPattern();
        fullPattern = c.getFullPattern();
        criteria = c.getCriteria();
    }

    public CardinalityConstraint(JsonElement node1, String edge1, JsonElement node2, int min, String max, String constraintPattern, String fullPattern, List<String> criteria){
        this.setNode1(node1);
        this.setEdge1(edge1);
        this.setNode2(node2);
        this.setMin(min);
        this.setMax(max);
        this.setConstraintPattern(constraintPattern);
        this.setFullPattern(fullPattern);
        this.setCriteria(criteria);
    }

    @Context
    public GraphDatabaseService db;

    public static String propertiesPattern = "", kCardPattern = "";

    public static List<String> criteriaList = new ArrayList<>();

    @Procedure(value = "card_constraint.create_kcard", mode = Mode.WRITE)
    @Description("create a k-vertex cardinality constraint")
    public void createConstraint(@Name("inputKCard") String inputString) {

        this.kCardPattern = "";
        this.propertiesPattern = "";
        this.criteriaList = new ArrayList<>();

        String input = inputString;
        System.out.println("Input string: " + input);

        Gson gson = new Gson();
        JsonParser jsonParser = new JsonParser();
        JsonObject inputKCard = (JsonObject)jsonParser.parse(input);
        System.out.println("Input JSON kCard: " + inputKCard.toString());

        parseRecursion(inputKCard);

        String parsedkCardPattern = "";
        if(!propertiesPattern.equals("")) {
            propertiesPattern = propertiesPattern.substring(0, propertiesPattern.lastIndexOf(" AND "));
            parsedkCardPattern = "MATCH " + kCardPattern + " WHERE " + propertiesPattern;
        }
        else
            parsedkCardPattern = kCardPattern;

        if(parsedkCardPattern.contains(")("))
            parsedkCardPattern = parsedkCardPattern.replace(")(", "), (");

        System.out.println("KCard pattern: " + kCardPattern);
        System.out.println("Criteria list: " + criteriaList);

        CardinalityConstraint kCard = new CardinalityConstraint(inputKCard.get("N1"),inputKCard.get("E1").toString(), inputKCard.get("N2"), inputKCard.get("min").getAsInt(),
                inputKCard.get("max").toString(),kCardPattern, parsedkCardPattern, criteriaList);
        constraints.add(kCard);

        System.out.println("KCARD: " + gson.toJson(kCard));
        String query = "CREATE (c:KCard {N1: \'" + kCard.getNode1() + "\', E1: \'" + kCard.getEdge1() + "\', " +
                "N2: \'" + kCard.getNode2() + "\', min: " + kCard.getMin() + ", max: \'" + kCard.getMax() + "\', " +
                " constraintPattern: \'" + kCardPattern + "\', fullPattern: \'" + parsedkCardPattern + "\', " +
                "criteria: \'" + criteriaList + "\'}) RETURN c";
        System.out.println("Query: " + query);
        db.executeTransactionally(query);
    }

    private void parseRecursion(JsonObject jsonObject){

        Set<String> keys = jsonObject.keySet();
        for (String key:
                keys) {
            if(key.contains("N")) {
                if(!jsonObject.get(key).isJsonArray()) {
                    if (jsonObject.get(key).toString().contains("(")) {
                        String propertyString = jsonObject.get(key).toString();

                        String propString =  propertyString.substring(propertyString.indexOf("(") + 1, propertyString.indexOf(")"));

                        String[] propsArray = propString.split(",");
                        for (int i = 0; i < propsArray.length; i++) {
                            propertiesPattern += key + "." + propsArray[i].replace(" ", "") + " AND ";
                            criteriaList.add(key + "." + propsArray[i].replace(" ", ""));
                        }
                        if(kCardPattern.contains(key + ":")) {
                            String last2Chars = kCardPattern.substring(kCardPattern.length()-2);
                            if(last2Chars.equals("->"))
                                kCardPattern += "(" + key + ":" + jsonObject.get(key).toString()
                                        .substring(0, propertyString.indexOf(propString) - 2).replace("\"", "") + ")";
                            else
                                kCardPattern += ", (" + key + ":" + jsonObject.get(key).toString()
                                        .substring(0, propertyString.indexOf(propString) - 2).replace("\"", "") + ")";
                        }
                        else {
                            kCardPattern += "(" + key + ":" + jsonObject.get(key).toString().substring(0, propertyString.indexOf(propString) - 2).replace("\"", "") + ")";
                        }
                    }
                    else{
                        if(kCardPattern.contains(key + ":")) {
                            String last2Chars = kCardPattern.substring(kCardPattern.length()-2);
                            if(last2Chars.equals("->"))
                                kCardPattern += "(" + key + ":" + jsonObject.get(key).toString().replace("\"", "") + ")";
                            else
                                kCardPattern += ", (" + key + ":" + jsonObject.get(key).toString().replace("\"", "") + ")";
                        }
                        else
                            kCardPattern += "(" + key + ":" + jsonObject.get(key).toString().replace("\"", "") + ")";
                    }
                }
                else{
                    JsonArray jsonArray = jsonObject.getAsJsonArray(key);
                    for(int i = 0; i < jsonArray.size(); i++)
                        parseRecursion(jsonArray.get(i).getAsJsonObject());
                }
            //    }
            }
            else if(key.contains("E")) {
                if(jsonObject.get(key).toString().contains("(")){
                    String propertyString = jsonObject.get(key).toString();
                    String propString =  propertyString.substring(propertyString.indexOf("(") + 1, propertyString.indexOf(")"));
                    String[] propsArray = propString.split(",");
                    for (int i = 0; i < propsArray.length; i++) {
                        propertiesPattern += key + "." + propsArray[i].replace(" ", "")+ " AND ";
                        criteriaList.add(key + "." + propsArray[i].replace(" ", ""));
                    }
                    propertyString = propertyString.substring(0, propertyString.indexOf(propString));

                    kCardPattern += "-[" + key + ":" + jsonObject.get(key).toString().substring(0, propertyString.indexOf(propString) -2).replace("\"", "")+ "]->";
                }
                else{
                    kCardPattern += "-[" + key + ":" + jsonObject.get(key).toString().replace("\"", "") + "]->";

                }
            }
        }
    }

    public JsonElement getNode1() {
        return node1;
    }

    public void setNode1(JsonElement node1) {
        CardinalityConstraint.node1 = node1;
    }

    public String getEdge1() {
        return edge1;
    }

    public void setEdge1(String edge1) {
        CardinalityConstraint.edge1 = edge1;
    }

    public JsonElement getNode2() {
        return node2;
    }

    public void setNode2(JsonElement node2) {
        CardinalityConstraint.node2 = node2;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        CardinalityConstraint.min = min;
    }

    public String getMax() {
        return max;
    }

    public void setMax(String max) {
        CardinalityConstraint.max = max;
    }

    public String getPattern() {
        return constraintPattern;
    }

    public void setPattern(String pattern) {
        CardinalityConstraint.constraintPattern = pattern;
    }

    public List<String> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<String> criteria) {
        CardinalityConstraint.criteria = criteria;
    }

    public String getConstraintPattern() {
        return constraintPattern;
    }

    public void setConstraintPattern(String constraintPattern) {
        CardinalityConstraint.constraintPattern = constraintPattern;
    }

    public String getFullPattern() {
        return fullPattern;
    }

    public void setFullPattern(String fullPattern) {
        CardinalityConstraint.fullPattern = fullPattern;
    }

    /*
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

    @Procedure(value = "card_constraint.create_kcard", mode = Mode.WRITE)
    @Description("create a k-vertex cardinality constraint")
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

        System.out.println("Params: " + jsonParams);

        String query = "CREATE (c:CardinalityConstraint {R: \'" + objectMap.get("R") + "\', E: \'" + objectMap.get("E") +
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

     */
}
