package card_constraint;

import au.com.bytecode.opencsv.CSVReader;
import com.google.gson.Gson;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
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

    //////////////////////////////////////////////////////////////////////////////////
    /*@Procedure(value = "card_constraint.check_constraint", mode = Mode.WRITE)
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

            */
/*
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

        }
        try {
            writeResults(outputResult, path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    //////////////////////////////////////////////////////////////////////////////////
    @Procedure(value = "card_constraint.create_relationship", mode = Mode.WRITE)
    @Description("create new relationship")
    public Stream<Output> createRelationship(@Name("query") String query, @Name("constraint_mode")String mode) {
        String message = "";

        PatternMatcher matcher = new PatternMatcher();

        /**
         * Parse input string and get nodes and relationships
         */
        matcher.parseNodesRelationships(query);


        String inputPattern = "";
        List<String> matches = matcher.getMatches();

        int numNodes = 0;

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
        matcher.setInputPattern(inputPattern);

        System.out.println("Input pattern: " + inputPattern);

        // no cardinality check
        if(mode.toLowerCase().equals("no_cardinality")){
            System.out.println("[CREATE NO CARDINALITY]");

            matcher.removeConditionsFromPattern();
            String pathQuery = matcher.getPatternVariables();

            dbQueryNoCard += " CREATE " + pathQuery;
            System.out.println("[CREATE] DB query: " + dbQueryNoCard);
            db.execute(dbQueryNoCard);
            message = Output.MESSAGE_TYPE.SUCCESS.text;
        } else{
            System.out.println("[CREATE CARDINALITY]");

            boolean isRuleViolated = false;

            /**
             * Remove conditions to compare patterns
             */
            Pattern conditionsPattern = Pattern.compile("\\{(.*?)\\}");
            Matcher conditionsMatcher = conditionsPattern.matcher(query);

            while(conditionsMatcher.find()){
                if (conditionsMatcher.group(1).contains(",")) {
                    String[] singleConditions = conditionsMatcher.group(1).split(",");
                    for (int i = 0; i < singleConditions.length; i++)
                        matcher.inputPatternMap.put(singleConditions[i].split(":")[0].trim(), singleConditions[i].split(":")[1].replaceAll("'", "").trim());
                } else
                    matcher.inputPatternMap.put(conditionsMatcher.group(1).split(":")[0].trim(), conditionsMatcher.group(1).split(":")[1].replaceAll("'", "").trim());
            }

            matcher.removeConditionsFromPattern();
            matcher.buildMapFromInputPattern();

            List<String> nodeTypes = matcher.getNodeTypes();

            List<LocalCardinalityConstraint> constraints = retrieveConstraints(null);

            boolean relExistsDB, relExistsPattern = true;

            int numRels = 0;
            for (LocalCardinalityConstraint constraint:
                 constraints) {
                // check for min

                TreeMap constraintMap = new TreeMap();
                constraintMap.put("E", constraint.nodeLabel);
                constraintMap.put("R", constraint.relType);
                constraintMap.put("S", constraint.subgraph);

                Structure structure = new Structure();
                Map recurConstraintMap = buildMapStructure(1, constraintMap, structure.structureMap, constraint.params);
                structure.structureMap.putAll(recurConstraintMap);

                String constraintPattern = "(n1:" + constraint.nodeLabel + ")-[r1:" + constraint.relType + "]->";

                constraintPattern = buildConstraintPattern(2, constraintPattern, constraint.subgraph, constraint.params);

                if(constraint.minKCard.intValue() == 1){
                    for (String inputNode:
                         nodeTypes) {
                        String nodeType = inputNode.split(":")[1].substring(0, inputNode.split(":")[1].indexOf("{"));

                        if(nodeType.trim().toLowerCase().equals(constraint.nodeLabel.toLowerCase())){

                            relExistsDB = checkRelationshipExistence(inputNode, constraintPattern);
                            System.out.println("EXISTS in DB: " + relExistsDB);

                            if(relExistsDB == false){
                                relExistsPattern = checkRelationshipInputPattern(matcher, constraint);
                                System.out.println("EXISTS in input pattern: " + relExistsPattern);

                                if(relExistsPattern == false){
                                    isRuleViolated = true;
                                    message += Output.MESSAGE_TYPE.MIN_VIOLATION.text;
                                    message += " ( Node label: " + constraint.nodeLabel;
                                    message += ", Relationship type:  " + constraint.relType;
                                    message += ", Subgraph: " + constraint.subgraph;
                                    message += ", Min: " + constraint.minKCard;
                                    message += ", Max: " + constraint.maxKCard;

                                    if (constraint.params != null)
                                        message += ", Params: " + constraint.params;
                                    message += ")";
                                }
                            }
                        }
                    }
                }

                    // check for max

                if (!constraint.maxKCard.equals("*")) {
                    System.out.println("Input pattern structure: " + matcher.getPatternWithoutConditions());

                    constraintPattern = constraintPattern.replaceAll("\\{.*?\\}", "");
                    constraintPattern = constraintPattern.replaceAll("\\,", "");
                    constraintPattern = constraintPattern.replaceAll("\\ ", "");
                    System.out.println("Constraint structure: " + constraintPattern);

                    if (matcher.getPatternWithoutConditions().equals(constraintPattern)) {

                        if (matcher.inputPatternMap.entrySet().containsAll(structure.structureMap.entrySet())) {
                            numRels = getCurrentNumberOfRels(inputPattern);

                            if (numRels >= Integer.parseInt(constraint.maxKCard)) {
                                isRuleViolated = true;
                                message += Output.MESSAGE_TYPE.MAX_VIOLATION.text;
                                message += " ( Node label: " + constraint.nodeLabel;
                                message += ", Relationship type:  " + constraint.relType;
                                message += ", Subgraph: " + constraint.subgraph;
                                message += ", Min: " + constraint.minKCard;
                                message += ", Max: " + constraint.maxKCard;

                                if (constraint.params != null)
                                    message += ", Params: " + constraint.params;
                                message += ")";
                            }
                        } else {
                            System.out.println("Hello");
                            isRuleViolated = true;
                            message += Output.MESSAGE_TYPE.CONSTRAINT_VIOLATION.text;
                            message += " ( Node label: " + constraint.nodeLabel;
                            message += ", Relationship type:  " + constraint.relType;
                            message += ", Subgraph: " + constraint.subgraph;
                            message += ", Min: " + constraint.minKCard;
                            message += ", Max: " + constraint.maxKCard;

                            if (constraint.params != null)
                                message += ", Params: " + constraint.params;
                            message += ")";
                        }
                    }
                }

            }

            if (isRuleViolated == false) {
                dbQueryCard += " CREATE " + matcher.getPatternWithoutConditions();
                System.out.println("[CREATE] DB query: " + dbQueryCard);
                db.execute(dbQueryCard);
                message += Output.MESSAGE_TYPE.SUCCESS.text;
            }
        }
        return Stream.of(new Output(message));
    }

    private String buildConstraintPattern(int recursionLevel, String constraintPattern, Map<String, Object> subgraphMap,
                                          Map<String, Object> params){
        TreeMap sortedMap = new TreeMap();
        sortedMap.putAll(subgraphMap);

        Iterator it = null, localIt = null;

        for(Object entry : sortedMap.entrySet()){
            Map.Entry property = (Map.Entry)entry;

            if(params != null) {
                it = params.entrySet().iterator();
            }

            switch(property.getKey().toString()){
                case "E":
                    constraintPattern += "(n" + recursionLevel + ":" + property.getValue();

                    if(params != null){
                        while(it.hasNext()){
                            Map.Entry paramEntry = (Map.Entry) it.next();

                            int paramEntryLevel = Integer.valueOf(paramEntry.getKey().toString().substring(1, 2));

                            if (paramEntryLevel == recursionLevel) {
                                Map localParams = (Map)paramEntry.getValue();

                                localIt = localParams.entrySet().iterator();
                                while(localIt.hasNext()){
                                    Map.Entry localEntry = (Map.Entry)localIt.next();
                                    if(localEntry.getKey().toString().equals("E")){
                                        List<Map> paramsList = (List<Map>)localEntry.getValue();
                                        for (Map paramsMap:
                                                paramsList) {
                                            if(paramsList.indexOf(paramsMap) == paramsList.size() - 1)
                                                constraintPattern += " {" + paramsMap.get("prop") + ":'" + paramsMap.get("value") + "'}";
                                            else
                                                constraintPattern += " {" + paramsMap.get("prop") + ":'" + paramsMap.get("value") + "'}, ";
                                        }
                                    }
                                }
                            }
                        }
                    }

                    constraintPattern +=  ")";
                    break;
                case "R":
                    constraintPattern += "-[r" + recursionLevel + ":" + property.getValue();

                    if(params != null){
                        while(it.hasNext()){

                            Map.Entry paramEntry = (Map.Entry) it.next();
                            int paramEntryLevel = Integer.valueOf(paramEntry.getKey().toString().substring(1, 2));

                            if (paramEntryLevel == recursionLevel) {
                                Map localParams = (Map)paramEntry.getValue();

                                localIt = localParams.entrySet().iterator();
                                while(localIt.hasNext()){
                                    Map.Entry localEntry = (Map.Entry)localIt.next();

                                    if(localEntry.getKey().toString().equals("R")){
                                        List<Map> paramsList = (List<Map>)  localEntry.getValue();
                                        for (Map paramsMap:
                                                paramsList) {
                                            if(paramsList.indexOf(paramsMap) == paramsList.size() - 1)
                                                constraintPattern += " {" + paramsMap.get("prop") + ":'" + paramsMap.get("value") + "'}";
                                            else
                                                constraintPattern += " {" + paramsMap.get("prop") + ":'" + paramsMap.get("value") + "'}, ";
                                        }
                                    }
                                }
                            }
                        }
                    }

                    constraintPattern += "]->";

                    break;
                case "S":
                    Map sMap = (Map) property.getValue();

                    constraintPattern = buildConstraintPattern((recursionLevel+1), constraintPattern, sMap, params);
                    break;
                default:
                    return constraintPattern;
            }
        }
        return constraintPattern;
    }

    private Map buildMapStructure(int recursionLevel, Map subgraphMap, Map structureMap, Map params){
        TreeMap sortedMap = new TreeMap();
        sortedMap.putAll(subgraphMap);

        Iterator it = null, localIt = null;

        for(Object entry : sortedMap.entrySet()){
            Map.Entry property = (Map.Entry)entry;

            if(params != null) {
                it = params.entrySet().iterator();
            }

            switch(property.getKey().toString()){
                case "E":
                    structureMap.put("n" + recursionLevel, property.getValue().toString());

                    if(params != null){
                        while(it.hasNext()){
                            Map.Entry paramEntry = (Map.Entry) it.next();
                            int paramEntryLevel = Integer.valueOf(paramEntry.getKey().toString().substring(1, 2));

                            if (paramEntryLevel == recursionLevel) {
                                Map localParams = (Map)paramEntry.getValue();

                                localIt = localParams.entrySet().iterator();
                                while(localIt.hasNext()){
                                    Map.Entry localEntry = (Map.Entry)localIt.next();
                                    if(localEntry.getKey().toString().equals("E")){
                                        List<Map> paramsList = (List<Map>)localEntry.getValue();
                                        for (Map paramsMap:
                                                paramsList) {
                                            structureMap.put(paramsMap.get("prop").toString(), paramsMap.get("value").toString());
                                        }
                                    }
                                }
                            }
                        }
                    }

                    break;
                case "R":
                    structureMap.put("r"+ recursionLevel, property.getValue().toString());

                    if(params != null){
                        while(it.hasNext()){
                            Map.Entry paramEntry = (Map.Entry) it.next();

                            int paramEntryLevel = Integer.valueOf(paramEntry.getKey().toString().substring(1, 2));
                            if (paramEntryLevel == recursionLevel) {
                                Map localParams = (Map)paramEntry.getValue();

                                localIt = localParams.entrySet().iterator();
                                while(localIt.hasNext()){
                                    Map.Entry localEntry = (Map.Entry)localIt.next();
                                    if(localEntry.getKey().toString().equals("R")){
                                        List<Map> paramsList = (List<Map>)localEntry.getValue();
                                        for (Map paramsMap:
                                                paramsList) {
                                            structureMap.put(paramsMap.get("prop").toString(), paramsMap.get("value").toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                  //  System.err.println("Structure Map " +  recursionLevel + ": " + structureMap.values());

                    break;
                case "S":
                    Map sMap = (Map) property.getValue();
                    recursionLevel = recursionLevel + 1;
                    structureMap = buildMapStructure(recursionLevel, sMap, structureMap, params);
                    break;
                default:
                    return structureMap;
            }
        }
        return structureMap;
    }

    private String buildDBCountQuery(String inputPattern){
        String strippedInput = inputPattern.replaceAll("\\{.*?\\}", "");

        Pattern relLabelPattern = Pattern.compile("\\[(.*?)\\]");
        Matcher relLabelMatcher = relLabelPattern.matcher(strippedInput);
        List<String> matches = new ArrayList<>();

        while(relLabelMatcher.find()){
            matches.add(relLabelMatcher.group(1).split(":")[0]);
        }

        String relationshipLabel = matches.get(0);

        String countQuery = "MATCH " + inputPattern + " RETURN COUNT(" + relationshipLabel + ") as number";

        return countQuery;
    }

    public int getCurrentNumberOfRels(String inputPattern) {
        long numRels = 0;

        String countQuery = buildDBCountQuery(inputPattern);

        System.out.println("Count query: " + countQuery);
        Result countResult = db.execute(countQuery);

        if (countResult.hasNext()) {
            while (countResult.hasNext()) {
                numRels = (long) countResult.next().get("number");

                System.out.println("[CREATE] Numrels: " + numRels);
            }
        }

        return (int)numRels;
    }

    public boolean checkRelationshipExistence(String startNode, String pattern) {
        boolean existsRel = false;

        pattern = pattern.substring(pattern.indexOf("-"),pattern.length());
        String dbQuery = "MATCH p=(" + startNode + ")" + pattern + " RETURN p";
        Result dbResults = db.execute(dbQuery);

        if (dbResults.hasNext())
            existsRel = true;
        return existsRel;
    }

    public boolean checkRelationshipInputPattern(PatternMatcher matcher, LocalCardinalityConstraint constraint){
        boolean existsRel = false;

        TreeMap constraintMap = new TreeMap();
        constraintMap.put("E", constraint.nodeLabel);
        constraintMap.put("R", constraint.relType);
        constraintMap.put("S", constraint.subgraph);

        Structure structure = new Structure();
        Map recurConstraintMap = buildMapStructure(1, constraintMap, structure.structureMap, constraint.params);
        structure.structureMap.putAll(recurConstraintMap);

        //build structure map for constraint pattern query

        if(matcher.inputPatternMap.entrySet().containsAll(structure.structureMap.entrySet())){
            existsRel = true;
        }

        return existsRel;
    }

    public List<LocalCardinalityConstraint> retrieveConstraints(String condition) {
        List<LocalCardinalityConstraint> constraints = new ArrayList<>();
        Gson gson = new Gson();
        Map<String, Object> map = new HashMap<>();

        Result resultConstraints = null;
        if (condition != null)
            resultConstraints = db.execute("MATCH (c:CardinalityConstraint) WHERE c.E = '" + condition + "' RETURN c");
        else
            resultConstraints = db.execute("MATCH (c:CardinalityConstraint) RETURN c");

        while (resultConstraints.hasNext()) {
            Map<String, Object> row = resultConstraints.next();
            Node n = (Node) row.get("c");
            long id = n.getId();
            String relType = n.getProperty("R").toString();
            String nodeLabel = n.getProperty("E").toString();
            map = gson.fromJson(n.getProperty("S").toString(), map.getClass());
            Number min = (long) n.getProperty("min");
            String max = n.getProperty("max").toString();
            Number k = (long) n.getProperty("k");

            Map params = null;
            if(n.hasProperty("params"))
               params = gson.fromJson(n.getProperty("params").toString(), map.getClass());
            LocalCardinalityConstraint constraint = new LocalCardinalityConstraint(id, relType, nodeLabel, map, min, max, k, params);

            constraints.add(constraint);
        }
        return constraints;
    }
}

