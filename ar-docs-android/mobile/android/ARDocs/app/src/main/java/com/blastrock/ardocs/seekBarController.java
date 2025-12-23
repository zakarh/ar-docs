package com.blastrock.ardocs;

public class seekBarController {
    private Integer min;
    private Integer max;
    private Integer current;

    seekBarController() {
    }

    seekBarController(Integer min, Integer max, Integer current) {
        this.min = min;
        this.max = max;
        this.current = current;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    public Integer getCurrent() {
        return current;
    }

    public void setCurrent(Integer current) {
        this.current = current;
    }
}
