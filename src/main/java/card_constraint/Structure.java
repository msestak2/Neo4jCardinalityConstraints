package card_constraint;

import java.util.Map;
import java.util.TreeMap;

public class Structure {
    public Map structureMap;

    public Structure() {
        this.structureMap = new TreeMap();
    }

    public Map getStructureMap() {
        return structureMap;
    }

    public void setStructureMap(Map structureMap) {
        this.structureMap = structureMap;
    }

}
