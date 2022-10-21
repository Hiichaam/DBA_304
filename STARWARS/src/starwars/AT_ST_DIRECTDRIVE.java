/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package starwars;

import ai.Choice;
import ai.DecisionSet;
import Environment.Environment;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class AT_ST_DIRECTDRIVE extends AT_ST{
 
    
    @Override
    public void setup() {       
        super.setup();

        A = new DecisionSet();
        A.
                addChoice(new Choice("MOVE")).
                addChoice(new Choice("LEFT")).
                addChoice(new Choice("RIGHT"));
        //problem="FlatNorth";
    }
    
    @Override
    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        problem = this.inputSelect("Please select problem to solve", problems, problem);
        if (problem == null) {
            return Status.CHECKOUT;
        }
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);
        
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.JOINSESSION;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    @Override
    public Status MyJoinSession() {
        this.DFAddMyServices(new String[]{"TYPE STF"});
        outbox = session.createReply();
        outbox.setContent("Request join session "+ sessionKey);
        LARVAsend(outbox);
        
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Confirm")){
            Error ("Couldn't join session "+sessionKey+" due "+session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        //this.openRemote();
        this.MyReadPerceptions();
        return MySolveProblem();
    }
    
    @Override
    public Status MySolveProblem(){
        if (G(E)) {
            Message("The problem " + problem + " has been solved");
            return Status.CLOSEPROBLEM;
        }

        Choice a = Ag(E, A);
        // If no choice is selected is due to a confusion of the Ag( ) function, then stop
        if (a == null) {
            Alert("Found no action to execute");
            return Status.CLOSEPROBLEM;
        } else {
            Info("Excuting " + a);
            this.MyExecuteAction(a.getName());
            this.MyReadPerceptions();
            if (!Ve(E)) {
                this.Error("The agent is not alive: " + E.getStatus());
                return Status.CLOSEPROBLEM;
            }
            
            // After all this, iterate again
            return Status.SOLVEPROBLEM;
        }
    }
    
    
    protected double goAhead(Environment E, Choice a) {
        if (a.getName().equals("MOVE")) {
           
            return U(S(E, a));
        } else if (a.getName().equals("LEFT") || a.getName().equals("RIGHT")) {
            
            return U(S(E, a), new Choice("MOVE"));
        }
        return Choice.MAX_UTILITY;
    }
    
    @Override
    protected double U(Environment E, Choice a) {
        return goAhead(E,a);
    }
    
    /*@Override
    public String easyPrintPerceptions() {
        if (!showPerceptions || !logger.isEcho()) {
            return "";
        }
        this.Prioritize(getEnvironment(), A);
        return super.easyPrintPerceptions()
                + "\nDECISION SET:\n" + A.toString() + "\n";
    }*/
}
