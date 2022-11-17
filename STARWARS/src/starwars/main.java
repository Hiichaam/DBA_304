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
        boot.launchAgent("DC-" + getHexaKey(), AT_ST_LAB2.class);
        boot.WaitToShutDown();    }
    
}
