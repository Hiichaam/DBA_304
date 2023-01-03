/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package starwars;
import appboot.JADEBoot;
import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;
public class main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es",1099);
        boot.launchAgent("SSD", agents.SSD.class);
        boot.loadAgent("DC1-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC2-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC3-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC4-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC5-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC6-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC7-" + getHexaKey(), AT_ST_LAB3.class);
        //boot.loadAgent("DC8-" + getHexaKey(), AT_ST_LAB3.class);
        boot.WaitToShutDown();    }
    
}
