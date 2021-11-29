package card_constraint;
import card_constraint.model.Criteria;
import card_constraint.model.LabelSpec;
import com.google.gson.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Main {

    @Context
    public GraphDatabaseService db;

    @Procedure(value = "card_constraint.create_edge", mode = Mode.WRITE)
    @Description("create new edge with checking kCards")
    public Stream<Output> createEdge(@Name("query") String query) {

        String message = "";
        boolean patternMatches = true, isMaxRuleViolated = false, isMinRuleViolated = false;
        boolean edgeExistsDB = false, edgeExistsPattern = false;
        String parsedSubQuery = query.split("WHERE")[0].replace(" ", "");
        String parsedCriteriaQuery = query.split("WHERE")[1];

        System.out.println("Input query pattern: " + parsedSubQuery);
        System.out.println("Input criteria: " + parsedCriteriaQuery);

        List<Criteria> inputCriteriaList = new ArrayList<>();
        String[] inputCriteriaArray = parsedCriteriaQuery.split(" AND ");
        for (int i = 0; i < inputCriteriaArray.length; i++) {
            System.out.println("InputCriteriaRow: " + inputCriteriaArray[i]);
            inputCriteriaList.add(extractCriteria(inputCriteriaArray[i]));
        }

        List<LocalCardinalityConstraint> matchingConstraints = retrieveConstraints(parsedSubQuery);
        System.out.println("Matching constraints size: " + matchingConstraints.size());
        if (matchingConstraints.size() != 0) {
            for (LocalCardinalityConstraint l :
                    matchingConstraints) {
                List<Criteria> kCardCriteriaList = new ArrayList<>();

                if (l.criteria.size() != 0) {
                    for (String singleCriteria : l.criteria) {
                        kCardCriteriaList.add(extractCriteria(singleCriteria));

                        for (Criteria cKCard :
                                kCardCriteriaList) {
                            Criteria cInput = inputCriteriaList.stream().filter(c -> c.getProperty().trim().equals(cKCard.getProperty().trim())).findFirst().orElse(null);
                            if (cInput != null) {
                                System.out.println("CKard: " + cKCard.getProperty().trim() + ", CInput: " + cInput.getProperty().trim());

                                switch (cKCard.getOperator().trim()) {
                                    case "=":
                                        if (!cKCard.getValue().trim().equals(cInput.getValue().trim())) {
                                            patternMatches = false;
                                            System.out.println("= not match");
                                            System.out.println("cKCard value: " + cKCard.getValue().trim());
                                            System.out.println("cInput value: " + cInput.getValue().trim());
                                        }
                                        break;
                                    case ">":
                                        if (Integer.valueOf(cInput.getValue()) <= Integer.valueOf(cKCard.getValue())) {
                                            System.out.println("> not match");
                                            patternMatches = false;
                                            System.out.println("cKCard value: " + cKCard.getValue().trim());
                                            System.out.println("cInput value: " + cInput.getValue().trim());
                                        }
                                        break;
                                    case "<":
                                        if (Integer.valueOf(cInput.getValue()) >= Integer.valueOf(cKCard.getValue())) {
                                            System.out.println("< not match");
                                            patternMatches = false;
                                            System.out.println("cKCard value: " + cKCard.getValue().trim());
                                            System.out.println("cInput value: " + cInput.getValue().trim());
                                        }
                                        break;
                                }
                            } else
                                patternMatches = false;
                        }
                    }
                }
                System.out.println("Pattern matched: " + patternMatches);

                if (patternMatches == true) {

                    if (!l.max.equals("*")) { //check for max violation
                        int numEdges = getCurrentNumberOfEdges(query);
                        if (numEdges >= Integer.valueOf(l.max)) {
                            isMaxRuleViolated = true;
                            message += Output.MESSAGE_TYPE.MAX_VIOLATION.text;
                            message += " ( Rule pattern: " + l.fullPattern;
                            message += ", Min: " + l.min;
                            message += ", Max: " + l.max;
                            message += ")";
                        }
                    }
                }
            }
        }

        ArrayList<String> nodeTypes = extractNodeTypes(query);
        for (String nodeType :
                nodeTypes) {
            LocalCardinalityConstraint kCard = getMatchingNodeConstraint(nodeType);
            if (kCard.min == 1) {
                // check DB
                String kCardAlias = "";
                if (kCard.node1.getAsString().equals("\"" + nodeType + "\""))
                    kCardAlias = "N1";
                else
                    kCardAlias = "N2";


                ArrayList<String> subCriteriaList = new ArrayList<>();
                String subCriteriaPattern = query.substring(query.indexOf("WHERE ") + 6);
                System.out.println("SubCriteriaPattern: " + subCriteriaPattern);
                String[] criteriaRow = subCriteriaPattern.split(" AND ");
                for (String s :
                        criteriaRow) {
                    if (s.contains(kCardAlias)) {
                        subCriteriaList.add(s.trim());
                    }
                }

                edgeExistsDB = checkEdgeInDB(kCard.constraintPattern, subCriteriaList, kCardAlias);
                System.out.println("Edge exists in DB: " + edgeExistsDB);


                // check pattern
                edgeExistsPattern = checkEdgeInPattern(query, kCard.constraintPattern, nodeType);
                System.out.println("Edge exists in pattern: " + edgeExistsPattern);

                if (edgeExistsDB == false && edgeExistsPattern == false) {
                    isMinRuleViolated = true;
                    message += Output.MESSAGE_TYPE.MIN_VIOLATION.text;
                    message += " ( Rule pattern: " + kCard.fullPattern;
                    message += ", Min: " + kCard.min;
                    message += ", Max: " + kCard.max;
                    message += ")";
                }
            }
        }

        if (isMaxRuleViolated == false && isMinRuleViolated == false) {
            message += Output.MESSAGE_TYPE.SUCCESS.text;

            String dbQuery = buildDBQuery(query, inputCriteriaList);
            System.out.println("DB query: " + dbQuery);

            db.executeTransactionally(dbQuery);
        }

        return Stream.of(new Output(message));
    }


    private String buildDBCountQuery(String inputPattern) {
        /*Pattern relLabelPattern = Pattern.compile("\\[(.*?)\\]");
        Matcher relLabelMatcher = relLabelPattern.matcher(inputPattern);
        List<String> matches = new ArrayList<>();

        while(relLabelMatcher.find()){
            matches.add(relLabelMatcher.group(1).split(":")[0]);
        }

        String relationshipLabel = matches.get(0);*/

        String countQuery = "MATCH " + inputPattern + " RETURN COUNT(E1) as number";

        return countQuery;
    }

    public int getCurrentNumberOfEdges(String inputPattern) {
        long numEdges = 0;

        String countQuery = buildDBCountQuery(inputPattern);

        System.out.println("Count query: " + countQuery);
        try (Transaction tx = db.beginTx()) {
            Result countResult = tx.execute(countQuery);

            if (countResult.hasNext()) {
                while (countResult.hasNext()) {
                    numEdges = (long) countResult.next().get("number");

                    System.out.println("[CREATE] NumEdges: " + numEdges);
                }
            }
        }

        return (int) numEdges;
    }

    public HashMap<String, ArrayList> getGroupedNumberOfEdge(String inputPattern) {

        HashMap<String, ArrayList> results = new HashMap<>();
        try (Transaction tx = db.beginTx()) {
            Result countResult = tx.execute(inputPattern + "  WITH type(E1) as edge, {node: N1, number: COUNT(E1)} as nodes return edge, collect(nodes) as result");

            while(countResult.hasNext()) {
                Map<String, Object> entry = countResult.next();

                String edgeType = entry.get("edge").toString();
                System.out.println("edge: " + edgeType);

                ArrayList row = (ArrayList) entry.get("result");

                results.put(edgeType, row);
            }
        }
        System.out.println("Results size: " + results.size());
        /*Iterator it = results.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry e = (Map.Entry) it.next();
            System.out.println("entry: " + e.getKey() +  " value count: " + ((ArrayList)e.getValue()).size());

            for (Object o:
                    (ArrayList)e.getValue()) {
                Map m = (Map)o;
                System.out.println("Node: " + m.get("node") + ", number: " + m.get("number"));
            }
        }*/
        return results;
    }

    public Criteria extractCriteria(String criteriaRow) {
        criteriaRow = criteriaRow.replace("[", "").replace("]", "");
        System.out.println("SingleCriteria: " + criteriaRow);
        String[] singleCriteriaSplit = criteriaRow.split("[=\\<\\>]"); //TODO: only simple comparison operators
        String operator = criteriaRow.substring(criteriaRow.indexOf(singleCriteriaSplit[1]) - 1, criteriaRow.indexOf(singleCriteriaSplit[1]));
        System.out.println("K: " + singleCriteriaSplit[0] + ", op: " + operator + ", V: " + singleCriteriaSplit[1]);

        return new Criteria(singleCriteriaSplit[0], operator, singleCriteriaSplit[1]);
    }

    public ArrayList<String> extractNodeTypes(String inputPattern) {
        ArrayList<String> nodeTypes = new ArrayList<>();

        Pattern nodesPattern = Pattern.compile("\\((.*?)\\)");
        Matcher nodesMatcher = nodesPattern.matcher(inputPattern);
        while (nodesMatcher.find()) {
            nodeTypes.add(nodesMatcher.group(1).split(":")[1]);
        }

        for (String s :
                nodeTypes) {
            System.out.println("Node types in input Pattern: " + s);
        }

        return nodeTypes;
    }

    public boolean checkEdgeInDB(String inputPattern, ArrayList<String> criteriaList, String alias) {
        boolean edgeExists = false;
        String dbQuery = "MATCH p=" + inputPattern + " WHERE ";
        for (String s :
                criteriaList) {
            if (criteriaList.indexOf(s) != criteriaList.size() - 1) {
                dbQuery += alias + s.substring(s.indexOf(".")) + " AND";
            } else {
                dbQuery += alias + s.substring(s.indexOf("."));
            }
        }
        dbQuery += " RETURN p";
        System.out.println("DB QUERY 1: " + dbQuery);

        try (Transaction tx = db.beginTx()) {
            Result dbResults = tx.execute(dbQuery);

            if (dbResults.hasNext())
                edgeExists = true;
        }
        return edgeExists;
    }

    public boolean checkEdgeInPattern(String inputPattern, String constraintPattern, String nodeType) {
        boolean patternContainsEdge = false;

        System.out.println("Node type: " + nodeType);
        ArrayList<String> inputPatternList = extractPatternElements(inputPattern);
        ArrayList<String> kCardPatternList = extractPatternElements(constraintPattern);

        for (String s :
                inputPatternList) {
            System.out.println("Input pattern elements: " + s);
        }

        for (String s :
                kCardPatternList) {
            System.out.println("Constraint pattern elements: " + s);
        }

        int index = inputPatternList.indexOf(nodeType);
        if (kCardPatternList.contains(nodeType)) {
            int kCardIndex = kCardPatternList.indexOf(nodeType);

            System.out.println("Input pattern edge: " + inputPatternList.get(index + 1));
            System.out.println("Constraint pattern edge: " + kCardPatternList.get(kCardIndex + 1));
            if (inputPatternList.get(index + 1).trim().equals(kCardPatternList.get(kCardIndex + 1).trim()))
                patternContainsEdge = true;
        }
        return patternContainsEdge;
    }

    public String buildDBQuery(String inputPattern, List<Criteria> criteria) {
        String query = "";
        Pattern varPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher varMatcher = varPattern.matcher(inputPattern);

        ArrayList<String> elementsList = new ArrayList<>();
        while (varMatcher.find()) {
            if (varMatcher.group(1) != null) {
                List<Criteria> cList = new ArrayList<Criteria>();
                for (Criteria c :
                        criteria) {
                    if (c.getProperty().contains(varMatcher.group(1).split(":")[0]))
                        cList.add(c);
                }
                System.out.println("cList size: " + cList.size());
                query += "MATCH (" + varMatcher.group(1);
                String criteriaString = "{";
                for (Criteria c : cList) {
                    if (cList.indexOf(c) == (cList.size()) - 1) {
                        if (c.getOperator().equals("="))
                            criteriaString += c.getProperty().substring(c.getProperty().indexOf(".") + 1) +
                                    ": " + c.getValue();
                        else
                            query += c.getProperty().substring(c.getProperty().indexOf(".") + 1) +
                                    c.getOperator() + c.getValue();
                    } else {
                        if (c.getOperator().equals("="))
                            criteriaString += c.getProperty().substring(c.getProperty().indexOf(".") + 1) +
                                    ": " + c.getValue() + ", ";
                        else
                            query += c.getProperty().substring(c.getProperty().indexOf(".") + 1) +
                                    c.getOperator() + c.getValue() + ", ";
                    }

                }
                query += criteriaString + "}) ";
                elementsList.add(varMatcher.group(1).split(":")[0]);
            } else if (varMatcher.group(2) != null)
                elementsList.add(varMatcher.group(2));
        }
        query += " CREATE p=";

        query += "(" + elementsList.get(0) + ")-[" + elementsList.get(1) + "]->(" + elementsList.get(2) + ")";

        query += " RETURN p";
        return query;
    }

    public LocalCardinalityConstraint getMatchingNodeConstraint(String nodeType) {
        LocalCardinalityConstraint kCard = new LocalCardinalityConstraint();
        List<String> criteria = new ArrayList<>();

        Gson gson = new Gson();
        try (Transaction tx = db.beginTx()) {
            String query = "MATCH (c:KCard) WHERE c.N1='\"" + nodeType + "\"' OR c.N2='\"" + nodeType + "\"' RETURN c";
            System.out.println("Node matching query: " + query);
            Result resultKCard = tx.execute(query);

            Iterator it = resultKCard.stream().iterator();
            while (it.hasNext()) {
                Map<String, Object> row = (Map<String, Object>) it.next();
                Node n = (Node) row.get("c");

                long id = ((Number) n.getId()).longValue();
                JsonElement node1 = gson.toJsonTree(n.getProperty("N1"));
                String edge1 = n.getProperty("E1").toString();
                JsonElement node2 = gson.toJsonTree(n.getProperty("N2"));
                int min = Integer.valueOf(n.getProperty("min").toString());
                String max = n.getProperty("max").toString();
                String constraintPattern = n.getProperty("constraintPattern").toString();
                String fullPattern = n.getProperty("fullPattern").toString();
                System.out.println("fullPattern: " + fullPattern);
                criteria = new ArrayList<String>(Arrays.asList(n.getProperty("criteria").toString().split(",")));
                System.out.println("criteria: " + criteria);
                kCard = new LocalCardinalityConstraint(id, node1,
                        edge1, node2, min, max, constraintPattern, fullPattern, criteria);
            }
        }
        return kCard;
    }

    public ArrayList<String> extractPatternElements(String pattern) {
        ArrayList<String> patternElements = new ArrayList<>();
        Pattern varPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher varMatcher = varPattern.matcher(pattern);

        while (varMatcher.find()) {
            if (varMatcher.group(1) != null)
                patternElements.add(varMatcher.group(1).split(":")[1].replaceAll("\"", ""));
            else if (varMatcher.group(2) != null)
                patternElements.add(varMatcher.group(2).split(":")[1].replaceAll("\"", ""));
        }
        return patternElements;
    }

    public List<LocalCardinalityConstraint> retrieveConstraints(String inputQueryPattern) {
        List<LocalCardinalityConstraint> constraints = new ArrayList<>();
        Gson gson = new Gson();
        List<String> criteria = new ArrayList<>();

        String query = "";
        if(inputQueryPattern != null){
            query = "MATCH (c:KCard) WHERE c.constraintPattern = '" + inputQueryPattern + "' RETURN c";
        }
        else
            query = "MATCH (c:KCard) RETURN c";

        try (Transaction tx= db.beginTx()) {

            Result result = tx.execute(query);

            while (result.hasNext()) {
                Map<String, Object> row = result.next();
                Node n = (Node) row.get("c");

                long id = ((Number) n.getId()).longValue();
                JsonElement node1 = gson.toJsonTree(n.getProperty("N1"));
                String edge1 = n.getProperty("E1").toString();
                JsonElement node2 = gson.toJsonTree(n.getProperty("N2"));
                int min = Integer.valueOf(n.getProperty("min").toString());
                String max = n.getProperty("max").toString();
                String constraintPattern = n.getProperty("constraintPattern").toString();
                String fullPattern = n.getProperty("fullPattern").toString();
                System.out.println("fullPattern: " + fullPattern);
                criteria = new ArrayList<String>(Arrays.asList(n.getProperty("criteria").toString().split(",")));
                System.out.println("criteria: " + criteria);
                LocalCardinalityConstraint constraint = new LocalCardinalityConstraint(id, node1,
                        edge1, node2, min, max, constraintPattern, fullPattern, criteria);

                constraints.add(constraint);
            }
            System.out.println("constraints list size: " + constraints.size());
            return constraints;
        }
    }

    @Procedure(value = "card_constraint.check_kcard", mode = Mode.WRITE)
    @Description("check for edges in database that break the cardinality constraints")
    public Stream<OutputLabels> checkCardinality(@Name("labelSpec") String labelSpec) {
        JsonParser jsonParser = new JsonParser();
        JsonArray inputLabelSpecArray = (JsonArray) jsonParser.parse(labelSpec);
        System.out.println("Number of label specs: " + inputLabelSpecArray.size());

        String outputLabels = "";
        ArrayList<LabelSpec> labelSpecs = new ArrayList<>();

        for(int i = 0; i < inputLabelSpecArray.size(); i++){
            JsonObject jo = (JsonObject) inputLabelSpecArray.get(i);
            labelSpecs.add(new LabelSpec(jo.get("edge").toString(), jo.get("min").getAsInt(), jo.get("max").getAsInt(), jo.get("label").toString()));
        }

        System.out.println("LabelSpecs size: " + labelSpecs.size());

        List<LocalCardinalityConstraint> constraints = retrieveConstraints(null);

        HashMap<String, ArrayList> edgeCountMap = new HashMap<>();
        List<LabelSpec> labelSpecsList = new ArrayList<>();

        for (LocalCardinalityConstraint c:
             constraints) {
            edgeCountMap.clear();
            labelSpecsList.clear();
            edgeCountMap = getGroupedNumberOfEdge(c.fullPattern);

            labelSpecsList = labelSpecs.stream().filter(spec -> spec.edge.equals(c.edge1)).collect(Collectors.toList());
            System.out.println("Label spec list size: " + labelSpecsList.size());

            System.out.println("Edge1: " +c.edge1);
            ArrayList nodesForEdge = edgeCountMap.get(c.edge1.trim().replace("\"", ""));
            System.out.println("NodesForEdge size: " + nodesForEdge.size());
            for (Object o: nodesForEdge) {
                Map m = (Map)o;
                System.out.println("Number: "  + m.get("number"));
                for (LabelSpec spec:
                     labelSpecsList) {
                    if(spec.getMin() == c.min && spec.getMax() == Integer.valueOf(c.max)) {
                        if ((long)m.get("number") >= spec.min && (long)m.get("number") <= spec.max) {
                            outputLabels += "KCard ID<" + c._id + "> - Node ID<" + ((Node)m.get("node")).getId() + ">: " + spec.label + "\n";
                        }
                    }
                }
            }
        }

        return Stream.of(new OutputLabels(outputLabels));
    }
}
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


