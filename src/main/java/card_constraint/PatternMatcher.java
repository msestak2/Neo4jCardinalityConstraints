package card_constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternMatcher {

    private String pathQuery;
    private String inputPattern;
    private String patternVariables;
    private List<String> matches;
    private List<String> inputArray;
    private List<String> nodeTypes;
    public Map inputPatternMap;

    public PatternMatcher() {
        this.matches = new ArrayList<>();
        this.pathQuery = "";
        this.inputArray = new ArrayList<>();
        this.nodeTypes = new ArrayList<>();
        this.inputPatternMap = new TreeMap();
        this.patternVariables = "";
    }

    public void parseNodesRelationships(String inputPath){
        Pattern nodesPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher nodesMatcher = nodesPattern.matcher(inputPath);

        this.pathQuery = "";
        while(nodesMatcher.find()){

            if (nodesMatcher.group(1) != null){
                this.matches.add(nodesMatcher.group(1));
                this.pathQuery += "(" + nodesMatcher.group(1).replaceAll("\\{.*?\\}", "").trim() + ")-";
                this.nodeTypes.add(nodesMatcher.group(1));
            } else if(nodesMatcher.group(2) != null) {
                this.matches.add(nodesMatcher.group(2));
                this.pathQuery += "[" + nodesMatcher.group(2).replaceAll("\\{.*?\\}", "").trim() + "]->";
            }

        }

        this.pathQuery = this.pathQuery.substring(0, this.pathQuery.length()-1);
    }

    public void removeConditionsFromPattern(){
        this.inputPattern = this.inputPattern.replaceAll("\\{.*?\\}", "");
        this.inputPattern = this.inputPattern.replaceAll("\\s", "");

        Pattern varPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher varMatcher = varPattern.matcher(inputPattern);

        List<String> variableMatches = new ArrayList<>();

        while(varMatcher.find()){
            if (varMatcher.group(1) != null) {
                variableMatches.add(varMatcher.group(1).split(":")[0]);
                this.inputArray.add(varMatcher.group(1).split(":")[1]);
            }
            else if(varMatcher.group(2) != null) {
                variableMatches.add(varMatcher.group(2).split(":")[1]);
                this.inputArray.add(varMatcher.group(2).split(":")[1]);;
            }
        }

        this.patternVariables += "(" + variableMatches.get(0) + ")-[:" + variableMatches.get(1) + "]->(" + variableMatches.get(2)
                + ")";

    }

    public void buildMapFromInputPattern(){
        Pattern varPattern = Pattern.compile("\\((.*?)\\)|\\[(.*?)\\]");
        Matcher varMatcher = varPattern.matcher(this.inputPattern);

        while(varMatcher.find()){
            if (varMatcher.group(1) != null)
                inputPatternMap.put(varMatcher.group(1).split(":")[0], varMatcher.group(1).split(":")[1].replaceAll("'", ""));
            else if(varMatcher.group(2) != null)
                inputPatternMap.put(varMatcher.group(2).split(":")[0], varMatcher.group(2).split(":")[1].replaceAll("'", ""));
        }
    }

    public String getPathQuery() {
        return pathQuery;
    }

    public List<String> getMatches() {
        return matches;
    }

    public String getInputPattern() {
        return inputPattern;
    }

    public void setInputPattern(String inputPattern) {
        this.inputPattern = inputPattern;
    }

    public List<String> getInputArray() {
        return inputArray;
    }

    public List<String> getNodeTypes() {
        return nodeTypes;
    }

    public Map getInputPatternMap() {
        return inputPatternMap;
    }

    public String getPatternVariables() {
        return patternVariables;
    }

    public void setPatternVariables(String patternVariables) {
        this.patternVariables = patternVariables;
    }
}
