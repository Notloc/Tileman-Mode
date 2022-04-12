package com.tileman;

import java.io.Serializable;

public class CustomizableFormula implements Serializable {

    public FormulaType formulaType;
    public double b;

    public CustomizableFormula(FormulaType formulaType, double b) {
        this.formulaType = formulaType;
        this.b = b;
    }

    public int solveForX(double y) {
        switch (formulaType) {
            case EXPONENTIAL:
                return (int)Math.pow(y/b, 0.5);
            default:
                return 0;
        }
    }

    public int solveForY(double x) {
        switch (formulaType) {
            case EXPONENTIAL:
                return (int)(b*x*x);
            default:
                return 0;
        }
    }

    public enum FormulaType {
        EXPONENTIAL,
    }
}
