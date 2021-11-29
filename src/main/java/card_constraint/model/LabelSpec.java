package card_constraint.model;

public class LabelSpec {
    public String edge;
    public int min;
    public int max;
    public String label;

    public LabelSpec(String edge, int min, int max, String label) {
        this.edge = edge;
        this.min = min;
        this.max = max;
        this.label = label;
    }

    public String getEdge() {
        return edge;
    }

    public void setEdge(String edge) {
        this.edge = edge;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
