package card_constraint;

import au.com.bytecode.opencsv.CSVReader;
import com.google.gson.Gson;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.procedure.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class Main {

    @Context
    public GraphDatabaseService db;

    @Procedure(value = "card_constraint.initialize_parse_csv")
    @Description("parse input data from file by manually parsing CSV file")
    public void initialize_parse_csv() {

        List<String[]> entries = readCSVFile("/Users/phdtest/Desktop/data.csv");

        System.out.println("Number of entries: " + entries.size());

        List<String> queries = new ArrayList<>();

        for (String[] entry
                : entries) {

            String query = "MERGE (d2:Department {Dept_Code: \"" + entry[0] + "\", Dept_Name: \"" +
                    entry[1] + "\"}) " +
                    "MERGE (p:Program {Prog_Code: \"" + entry[4] + "\", Program_Name: \"" +
                    entry[5] + "\"}) " +
                    "MERGE (d1:Department {Dept_Code: \"" + entry[2] + "\", Dept_Name: \"" + entry[3] +
                    "\"}) " +
                    "MERGE (f:Source_Fund {Source_Fund_Code: \"" + entry[7] + "\", Source_Fund_Name: " +
                    "\"" + entry[8] + "\"}) " +
                    "MERGE (a:Account {Account_Code: \"" + entry[9] + "\", Account_Name: \"" + entry[10]
                    + "\"}) " +
                    "MERGE (y:Fiscal_Year {Year: \"" + entry[12] + "\"}) " +
                    "MERGE (d1)-[:IS_SUBDEPARTMENT_OF]->(d2) " +
                    "MERGE (d2)-[:HAS_PROGRAM]->(p) " +
                    "MERGE (p)-[:RUNS_IN]->(y) " +
                    "MERGE (f)-[:SPONSORS]->(p) " +
                    "MERGE (f)-[:USES]->(a)";

            queries.add(query);


        }

        try {
            writeResults(queries, "/Users/phdtest/Desktop/cypher.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    ;

    @Procedure(value = "card_constraint.load", mode = Mode.WRITE)
    @Description("create nodes and relationships")
    public void load_simple() {

        List<String> queries = readTXTFile("/Users/phdtest/Desktop/cypher.txt");


        for(int i = 0; i < queries.size(); i++){
            db.execute(queries.get(i));
        }
    }

    @Procedure(value = "card_constraint.reset", mode = Mode.WRITE)
    @Description("delete all nodes and relationships")
    public void reset() {

        String query = "MATCH (n)-[r]-() DETACH DELETE n,r";

        db.execute(query);

    }

    @Procedure(value = "card_constraint.generate", mode = Mode.WRITE)
    @Description("generate nodes and relationships")
    public void generateGraph(@Name("no_nodes")long noNodes, @Name("index")long index) {

        String degrees = "[";

        for(int i = 0; i < noNodes; i++){
            if((i+1) == noNodes)
                degrees += "1]";
            else
                degrees += "1,";
        }
        db.execute("CALL apoc.generate.simple(" + degrees + ", 'Label" + index + "', 'Type" + index + "')");

    }

    @Procedure(value = "card_constraint.generateComplete", mode = Mode.WRITE)
    @Description("generate complete graph")
    public void generateCompleteGraph(@Name("no_nodes")long noNodes, @Name("index")long index) {

        db.execute("CALL apoc.generate.complete(" + noNodes + ", 'Label" + index + "', 'Type" + index + "')");

    }

    //////////////////////////////////////////////////////////////////////////////
    @Procedure(value = "card_constraint.check_constraint", mode = Mode.WRITE)
    @Description("check for relationships in database that break the cardinality constraints")
    public void checkCardinality(@Name("filePath")String path) {
        List<LocalCardinalityConstraint> constraints = retrieveConstraints(null);

        Iterator iterator = constraints.iterator();
        List<Relationship> listRegularRels = new ArrayList<>();
        List<String> outputResult = new ArrayList<>();

        List<String> outputDeleteCmds = new ArrayList<>();

        Map<String, Integer> dataMap = new HashMap<>();

        Set<Relationship> uniqueRedundantRels = new HashSet<>();
        while (iterator.hasNext()) {
            dataMap.clear();
            listRegularRels.clear();
            uniqueRedundantRels.clear();

            LocalCardinalityConstraint c = (LocalCardinalityConstraint) iterator.next();

            String constraintPattern= "(n1:" + c.nodeLabel + ")-[r1:" + c.relType + "]->";
            constraintPattern = buildSubgraphPattern(2, constraintPattern, c.subgraph);

            System.out.println("\n[CHECK] Constraint:  " + constraintPattern);

            /**
             * Retrieve first node, last node and first relationships labels to retrieve number of relationships
             **/
            Pattern nodesPattern = Pattern.compile("\\((.*?)\\)");
            Matcher nodesMatcher = nodesPattern.matcher(constraintPattern);
            List<String> nodesArray = new ArrayList<>();

            while(nodesMatcher.find()){
                nodesArray.add(nodesMatcher.group(1));
            }

            String lastNodeTag = nodesArray.get(nodesArray.size()-1).split(":")[0];
            String dataQuery = "";

            if(c.k.intValue() == 1){
                dataQuery = "MATCH " + constraintPattern + " RETURN n1, r1";
            } else{
                dataQuery = "MATCH " + constraintPattern + " RETURN n1, " + lastNodeTag + ", r1";
            }
            System.out.println("[CHECK] Data query: " + dataQuery);
            Result dataResult = db.execute(dataQuery);

            while(dataResult.hasNext()){
                Map<String, Object> dataRow = dataResult.next();
                Node node1 = (Node)dataRow.get("n1");
                Node node2 = (Node)dataRow.get(lastNodeTag);
                Relationship rel = (Relationship)dataRow.get("r1");

                if(c.k.intValue() == 1){
                    if(dataMap.containsKey(Long.toString(rel.getStartNodeId()))){
                        int currentNoRels = (int)dataMap.get(Long.toString(rel.getStartNodeId()));
                        if(currentNoRels < c.maxKCard.intValue()){
                            dataMap.computeIfPresent(Long.toString(rel.getStartNodeId()), (k, v) -> v+1);
                            listRegularRels.add(rel);
                        } else {
                            System.out.println("[CHECK] Found redundant relationship! Rel ID: " + rel.getId());
                            uniqueRedundantRels.add(rel);
                        }
                    } else{
                        dataMap.put(Long.toString(rel.getStartNodeId()), 1);
                    }
                } else{
                    if(dataMap.containsKey(Long.toString(rel.getStartNodeId()) + "-" + Long.toString(node2.getId()))){
                        int currentRels = (int) dataMap.get(Long.toString(rel.getStartNodeId()) + "-" +
                                Long.toString(node2.getId()));
                        if(currentRels < c.maxKCard.intValue()){
                            dataMap.computeIfPresent(Long.toString(rel.getStartNodeId()) + "-" +
                                    Long.toString(node2.getId()), (k, v) -> v+1);
                            listRegularRels.add(rel);

                        } else{
                            uniqueRedundantRels.add(rel);
                        }
                    } else{
                        dataMap.put(Long.toString(rel.getStartNodeId()) + "-" + Long.toString(node2.getId()), 1);
                    }
                }

            }

            System.out.println("[CHECK] Regular list size: " + listRegularRels.size());
            System.out.println("[CHECK] Unique redundant set size: " + uniqueRedundantRels.size());
            System.out.println("[CHECK] Relationship counter map size: " + dataMap.entrySet().size());

            String format = "%-7s %-30s%n";
            outputResult.add(String.format(format, "Constraint ID:", c._id));
            outputResult.add(String.format(format, "Description:", constraintPattern));
            outputResult.add(String.format(format, "Cardinality:", "(" + c.minKCard + ", " + c.maxKCard + ")"));
            outputResult.add(String.format(format, "No of redundant relationships:", uniqueRedundantRels.size()));

            for (Relationship r:
                    uniqueRedundantRels) {
                if(c.k.intValue() == 1)
                    db.execute("MATCH ()-[r1]-() WHERE id(r1)=" + r.getId() + " DELETE r1");
                else
                    db.execute("MATCH ()-[r1]-()--() WHERE id(r1)=" + r.getId() + " DELETE r1");

            }
            System.out.println("[CHECK] Deleted redundant relationships!");
            /*if (c.k.longValue() == 1) {

                pattern = "(n:" + c.nodeLabel + ")-[r:" + c.relType + "]->(m:" + c.subgraph.get("E") + ")";
                System.out.println("Query k=1: " + pattern);


                Result resultRel = db.execute("MATCH " + pattern + " RETURN n, COUNT(r)");


                while (resultRel.hasNext()) {

                    int counterMatch = 0;
                    Map<String, Object> rowResult = resultRel.next();
                    long numRels = (long) rowResult.get("COUNT(r)");
                    Node n = (Node) rowResult.get("n");

                    if (!(numRels >= c.minKCard.longValue() && numRels <= c.maxKCard.longValue())) {
                        if (numRels < c.minKCard.longValue()) {
                            // System.out.println("[Constraint: " + c._id + "] Relationships missing!");
                            // writeResults( "/Users/martinasestak/Desktop/check_result.txt");
                        }
                        if (numRels > c.maxKCard.longValue()) {
                            //  System.out.println("[Constraint " + c._id + "] Too many relationships!");

                            Result resultNodesRels = db.execute("MATCH " + pattern + " RETURN n, r");

                            while (resultNodesRels.hasNext()) {
                                Map<String, Object> rowRel = resultNodesRels.next();
                                Node nRel = (Node) rowRel.get("n");

                                if (n.equals(nRel)) {
                                    Relationship r = (Relationship) rowRel.get("r");
                                    counterMatch++;

                                    if (counterMatch <= c.maxKCard.intValue()) {
                                        listRegularRels.add(r);
                                    } else {
                                        redundantConstraintCounter++;
                                        listRedundantRels.add(r);
                                    }
                                }
                            }

                        }
                    }
                }

            }*/


        }
        try {
            writeResults(outputResult, path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    @Procedure(value = "card_constraint.create_relationship", mode = Mode.WRITE)
    @Description("create new relationship")
    public void createRel(@Name("query") String query, @Name("constraint_mode")String mode) {
        // "(:Department)-[]->() {Dept_Code: ''})-[:IS_SUBDEPARMENT_OF]->(:Department {Dept_Code: ''})"
        int numNodes = 0;
        String inputPattern = "", pathQuery = "";

        /**
         * Parse input string and get nodes and relationships
         */
        Pattern nodesPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher nodesMatcher = nodesPattern.matcher(query);
        List<String> matches = new ArrayList<>();

        while(nodesMatcher.find()){

            if (nodesMatcher.group(1) != null){
                matches.add(nodesMatcher.group(1));
                pathQuery += "(" + nodesMatcher.group(1).split(":")[0] + ")-";
            } else if(nodesMatcher.group(2) != null) {
                matches.add(nodesMatcher.group(2));
                pathQuery += "[" + nodesMatcher.group(2) + "]->";
            }

        }

        pathQuery = pathQuery.substring(0, pathQuery.length()-1);

        String firstNode = matches.get(0);
        String dbQueryNoCard = "MATCH ", dbQueryCard = "MATCH ";

        for(int i = 0; i<matches.size(); i++){
            numNodes++;

            if(matches.size() > i+1){
                dbQueryNoCard += "(" + matches.get(i) + "), ";
                dbQueryCard += "(" + matches.get(i) + "), ";

                inputPattern += "(" + matches.get(i) + ")-[" + matches.get(++i) + "]->";

            } else{
                inputPattern += "(" + matches.get(i) + ")";
                dbQueryNoCard += "(" + matches.get(i) + ")";
                dbQueryCard += "(" + matches.get(i) + ")";

            }
        }
        System.out.println("Input pattern: " + inputPattern);

        // no cardinality check
        if(mode.toLowerCase().equals("no_cardinality")){
            System.out.println("[CREATE NO CARDINALITY]");

            dbQueryNoCard += " CREATE " + pathQuery;
            //"MATCH (" + firstNode + ") MERGE " + subPattern + " CREATE " + pathQuery;
            System.out.println("[CREATE] DB query: " + dbQueryNoCard);
            db.execute(dbQueryNoCard);
            // System.out.println("[CREATE] Created relationship!");
        } else{
            /**
             * Retrieve all constraints from database
             */
            System.out.println("[CREATE CARDINALITY]");
            List<LocalCardinalityConstraint> constraints = retrieveConstraints(null);

            for(LocalCardinalityConstraint c: constraints){
                if(c.k.intValue() == (numNodes-1)){

                    String constraintPattern= "(n1:" + c.nodeLabel + ")-[r1:" + c.relType + "]->";
                    constraintPattern = buildSubgraphPattern(2, constraintPattern, c.subgraph);

                    /**
                     * Remove conditions to compare patterns
                     */
                    Pattern conditionsPattern = Pattern.compile("\\{(.*?)\\}");
                    Matcher conditionsMatcher = conditionsPattern.matcher(query);

                    List<String> conditionsList = new ArrayList<>();

                    while(conditionsMatcher.find()){
                        conditionsList.add(conditionsMatcher.group(1));
                    }
                    String firstNodeCondition = conditionsList.get(0);
                    String lastNodeCondition = conditionsList.get(conditionsList.size()-1);
                    //  System.out.println("Conditions: " + firstNodeCondition + " - " + lastNodeCondition);

                    List<String> keysList = new ArrayList<>();
                    List<String> valuesList = new ArrayList<>();

                    keysList.add(firstNodeCondition.split(":")[0]);
                    keysList.add(lastNodeCondition.split(":")[0]);
                    valuesList.add(firstNodeCondition.split(":|,")[1]);
                    valuesList.add(lastNodeCondition.split(":|,")[1]);

                    inputPattern = inputPattern.replaceAll("\\{.*?\\}", "");
                    inputPattern = inputPattern.replaceAll("\\s", "");

                    Pattern varPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
                    Matcher varMatcher = varPattern.matcher(inputPattern);

                    List<String> variableMatches = new ArrayList<>();

                    while(varMatcher.find()){
                        if (varMatcher.group(1) != null)
                            variableMatches.add(varMatcher.group(1).split(":")[0]);
                        else if(varMatcher.group(2) != null)
                            variableMatches.add(varMatcher.group(2).split(":")[1]);
                    }

                    pathQuery += "(" + variableMatches.get(0) + ")-[:" + variableMatches.get(1) + "]->(" + variableMatches.get(2)
                            + ")";

                    if(inputPattern.equals(constraintPattern)){
                        /**
                         * Retrieve first node, last node and first relationships labels to retrieve number of relationships
                         **/
                        String firstNodeLabel = matches.get(0).split(":")[0];
                        String firstRelationshipLabel = matches.get(1).split(":")[0];
                        String lastNodeLabel = matches.get(matches.size()-1).split(":")[0];

                        String countQuery = "";
                        if(c.k.intValue() == 1){//condition only first node
                            countQuery = "MATCH " + inputPattern + " WHERE " + firstNodeLabel + "." + keysList.get(0) +
                                    "=" + valuesList.get(0) + " RETURN " + firstNodeLabel + ", COUNT(" +
                                    firstRelationshipLabel + ")";
                        } else {
                            countQuery = "MATCH " + inputPattern + " WHERE " + firstNodeLabel + "." + keysList.get(0) +
                                    "=" + valuesList.get(0) + " AND " + lastNodeLabel + "." + keysList.get(1) + "=" +
                                    valuesList.get(1) + " RETURN " + firstNodeLabel + ", " + lastNodeLabel +
                                    ", COUNT(" + firstRelationshipLabel + ")";
                        }
                        //System.out.println("Count query: " + countQuery);
                        Result countResult = db.execute(countQuery);

                        long numRels = 0;
                        if(!countResult.hasNext()){
                            dbQueryCard += " MERGE " + pathQuery;
                            System.out.println("[CREATE] DB query: " + dbQueryCard);
                            db.execute(dbQueryCard);
                            System.out.println("[CREATE] Created relationship!");

                        }
                        while (countResult.hasNext()) {
                            numRels = (long) countResult.next().get("COUNT(" + firstRelationshipLabel + ")");

                            System.out.println("[CREATE] Numrels: " + numRels);
                            if (numRels < c.maxKCard.intValue()) {
                                dbQueryCard += "MERGE " + pathQuery;
                                System.out.println("[CREATE] DB query: " + dbQueryCard);
                                db.execute(dbQueryCard);
                                System.out.println("[CREATE] Created relationship!");
                            }
                        }

                    }
                }
            }
        }

    }

    /////////////////////////////////////////////////////////////////////////////
    @Procedure(value = "card_constraint.create_relationship_min", mode = Mode.WRITE)
    @Description("create new relationship with minimum cardinality check")
    public Stream<Output> createRelationshipMinimum(@Name("query") String query, @Name("constraint_mode") String mode) {
        String message = "";

        int numNodes = 0;
        String inputPattern = "", pathQuery = "";

        List<String> inputArray = new ArrayList<>();

        /**
         * Parse input string and get nodes and relationships
         */
        Pattern nodesPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher nodesMatcher = nodesPattern.matcher(query);
        List<String> matches = new ArrayList<>();

        List<String> nodeTypes = new ArrayList<>();

        while (nodesMatcher.find()) {

            if (nodesMatcher.group(1) != null) {
                matches.add(nodesMatcher.group(1));
                pathQuery += "(" + nodesMatcher.group(1).split(":")[0] + ")-";
            } else if (nodesMatcher.group(2) != null) {
                matches.add(nodesMatcher.group(2));
                pathQuery += "[" + nodesMatcher.group(2) + "]->";
            }

        }

        pathQuery = pathQuery.substring(0, pathQuery.length() - 1);

        String firstNode = matches.get(0);
        String dbQueryNoCard = "MATCH ", dbQueryCard = "MATCH ";

        for (int i = 0; i < matches.size(); i++) {
            numNodes++;

            if (matches.size() > i + 1) {
                dbQueryNoCard += "(" + matches.get(i) + "), ";
                dbQueryCard += "(" + matches.get(i) + "), ";

                inputPattern += "(" + matches.get(i) + ")-[" + matches.get(++i) + "]->";

            } else {
                inputPattern += "(" + matches.get(i) + ")";
                dbQueryNoCard += "(" + matches.get(i) + ")";
                dbQueryCard += "(" + matches.get(i) + ")";

            }
        }
        System.out.println("Input pattern: " + inputPattern);

        if (mode.toLowerCase().equals("no_cardinality")) {
            System.out.println("[CREATE NO CARDINALITY]");

            dbQueryNoCard += " CREATE " + pathQuery;
            System.out.println("[CREATE] DB query: " + dbQueryNoCard);
            db.execute(dbQueryNoCard);
            message = "[CREATE] Created relationship without checking cardinality!";
        } else {
            LocalCardinalityConstraint violatedConstraint = null;
            boolean rule_violated = false;

            /**
             * Remove conditions to extract node types
             */
            Pattern conditionsPattern = Pattern.compile("\\{(.*?)\\}");
            Matcher conditionsMatcher = conditionsPattern.matcher(query);

            List<String> conditionsList = new ArrayList<>();

            while (conditionsMatcher.find()) {
                conditionsList.add(conditionsMatcher.group(1));
            }

            inputPattern = inputPattern.replaceAll("\\{.*?\\}", "");
            inputPattern = inputPattern.replaceAll("\\s", "");

            Pattern varPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
            Matcher varMatcher = varPattern.matcher(inputPattern);

            List<String> variableMatches = new ArrayList<>();

            while (varMatcher.find()) {
                if (varMatcher.group(1) != null) {
                    nodeTypes.add(varMatcher.group(1).split(":")[1]);
                    inputArray.add(varMatcher.group(1).split(":")[1]);
                } else if (varMatcher.group(2) != null) {
                    inputArray.add(varMatcher.group(2).split(":")[1]);
                }
            }

            // for each node in the input path, check to see if there is kCard with minimum cardinality of 1
            for (String nodeType :
                    nodeTypes) {
                List<LocalCardinalityConstraint> nodeConstraints = retrieveConstraints(nodeType);

                for (LocalCardinalityConstraint constraint :
                        nodeConstraints) {
                    if (constraint.minKCard.intValue() == 1) {
                        String nodeLabel = constraint.nodeLabel;
                        String relType = constraint.relType;
                        Map subgraph = constraint.subgraph;

                        String subgraphNode = subgraph.get("E").toString();

                        System.out.println("Constraint: ");
                        System.out.println("\t" + nodeLabel);
                        System.out.println("\t" + relType);
                        System.out.println("\t" + subgraphNode);

                        Iterator it = inputArray.iterator();
                        String firstElement, secondElement, thirdElement = "";
                        while (it.hasNext()) {
                            firstElement = it.next().toString();

                            if (firstElement.toLowerCase().equals(nodeLabel.toLowerCase())) {
                                System.out.println("FIRST: " + firstElement);

                                if (it.hasNext()) {
                                    secondElement = it.next().toString();
                                    System.out.println("SECOND: " + secondElement);
                                    if (!secondElement.toLowerCase().equals(relType.toLowerCase())) {
                                        System.err.println("VIOLATION!");
                                        rule_violated = true;
                                        violatedConstraint = constraint;
                                    } else {
                                        if (it.hasNext()) {
                                            thirdElement = it.next().toString();
                                            System.out.println("THIRD: " + thirdElement);

                                            if (!thirdElement.toLowerCase().equals(subgraphNode.toLowerCase())) {
                                                System.err.println("VIOLATION!");
                                                rule_violated = true;
                                                violatedConstraint = constraint;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }

            if (rule_violated == true) {
                message += "[WARNING] One of the input nodes requires a relationship to be created!";
                message += " ( Node label: " + violatedConstraint.nodeLabel;
                message += ", Relationship type:  " + violatedConstraint.relType;
                message += ", Subgraph: " + violatedConstraint.subgraph;
                message += ", Min: " + violatedConstraint.minKCard;
                message += ", Max: " + violatedConstraint.maxKCard + ")";
            } else {
                dbQueryCard += " MERGE " + pathQuery;
                System.out.println("[CREATE] DB query: " + dbQueryCard);
                db.execute(dbQueryCard);
                message += "[SUCCESS] Created relationship!";
            }
        }
        return Stream.of(new Output(message));

    }

    ////////////////////////////////////////////////////////////////////
    private List<String[]> readCSVFile(String path) {

        String[] line = new String[14];

        List<String[]> entries = new ArrayList<>();
        try {
            CSVReader reader = new CSVReader(new FileReader(path), ',');
            while ((line = reader.readNext()) != null) {
                if (!line[0].equals("Dept_Code"))
                    entries.add(line);
            }
            System.out.println("Entries size: " + entries.size());
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return entries;
    }

    private List<String> readTXTFile(String path) {
        String splitBy = ";";
        String line = "";

        List<String> queries = new ArrayList<String>();

        File file = new File(path);
        try {
            Scanner input = new Scanner(file);

            while (input.hasNextLine()) {
                queries.add(input.nextLine());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return queries;
    }

    private void writeResults(List<String> queries, String outputPath) throws IOException {

        FileWriter writer = null;

        try {
            writer = new FileWriter(outputPath);

            for (String query : queries) {
                writer.write(query);
                writer.write(System.getProperty("line.separator"));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        writer.close();

    }

    private String buildSubgraphPattern(int recursionLevel, String constraintPattern, Map<String, Object> subgraphMap){
        TreeMap sortedMap = new TreeMap();
        sortedMap.putAll(subgraphMap);

        for(Object entry : sortedMap.entrySet()){
            Map.Entry property = (Map.Entry)entry;
            switch(property.getKey().toString()){
                case "E":
                    constraintPattern += "(n" + recursionLevel + ":" + property.getValue() + ")";

                    break;
                case "R":
                    constraintPattern += "-[r" + recursionLevel + ":" + property.getValue() + "]->";

                    break;
                case "S":
                    Map sMap = (Map) property.getValue();

                    constraintPattern = buildSubgraphPattern((recursionLevel+1), constraintPattern, sMap);
                    break;
                default:
                    return constraintPattern;
            }
        }
        return constraintPattern;
    }

    public List<LocalCardinalityConstraint> retrieveConstraints(String condition) {
        List<LocalCardinalityConstraint> constraints = new ArrayList<>();
        Gson gson = new Gson();
        Map<String, Object> map = new HashMap<>();

        Result resultConstraints = null;
        if (condition != null)
            resultConstraints = db.execute("MATCH (c:Card_Constraint) WHERE c.E = '" + condition + "' RETURN c");
        else
            resultConstraints = db.execute("MATCH (c:Card_Constraint) RETURN c");

        while (resultConstraints.hasNext()) {
            Map<String, Object> row = resultConstraints.next();
            Node n = (Node) row.get("c");
            long id = n.getId();
            String relType = n.getProperty("R").toString();
            String nodeLabel = n.getProperty("E").toString();
            map = gson.fromJson(n.getProperty("S").toString(), map.getClass());
            Number min = (long) n.getProperty("min");
            Number max = (long) n.getProperty("max");
            Number k = (long) n.getProperty("k");

            LocalCardinalityConstraint constraint = new LocalCardinalityConstraint(id, relType, nodeLabel, map, min, max, k);

            constraints.add(constraint);

        }
        return constraints;
    }

    public enum ConstraintMode {
        NO_CARDINALITY, CARDINALITY
    }
}

