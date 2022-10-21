/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package starwars;

import Environment.Environment;
import ai.Choice;

public class AT_ST_BASIC_AVOID extends AT_ST_DIRECTDRIVE {

    // New refacotiring of the Utility function. In this case we split it into two 
    // different cases:moving and avoiding
    @Override
    protected double U(Environment E, Choice a) {
        
        if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else {
            return goAhead(E, a);
        }
    }

    
    
    public double goAvoid(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            return Choice.ANY_VALUE; // I give the choice "RIGHT" a positive value, any
        }
        return Choice.MAX_UTILITY; // and the others just a penalty with the max value, so that the decisoin is clear, isn't it?
    }

}
