package starwars;

import agents.BB1F;
import agents.DEST;
import agents.LARVAFirstAgent;
import agents.MTT;
import agents.SSD;
import ai.Choice;
import ai.DecisionSet;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AT_ST_LAB3 extends AT_ST_LAB2 {
    // Alias de nuestra sesion 
    String sessionAlias = "G304";
    ACLMessage controller;

    public void setup() {
        this.defSessionAlias(sessionAlias);

        super.setup();
    }

    @Override
    public void Execute() {
        Info("Status: " + myStatus.name());
        switch (myStatus) {
            case START:
                myStatus = Status.CHECKIN;
                break;
            case CHECKIN:
                myStatus = MyCheckin();
                break;
            case JOINSESSION:
                myStatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                myStatus = MySolveProblem();
                break;
            case CHECKOUT:
                myStatus = MyCheckout();
                break;
            case EXIT:
            default:
                doExit();
                break;
        }
    }

    @Override
    public AT_ST_FULL.Status MyJoinSession(){
        ArrayList<String> allSM = new ArrayList<>();
        allSM = this.DFGetAllProvidersOf("SESSION MANAGER");
        
        for (String sm : allSM){
            System.out.println(sm);
            if (this.DFHasService(sm, sessionAlias)){
                session.addReceiver(new AID(sm, AID.ISLOCALNAME));
            }
        }
        
        current_city = "Whitehorse";
        
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        this.DFAddMyServices(new String[]{"TEAM " + sessionAlias});
        outbox = session.createReply();
        
        // Iniciamos sesión en las coordenadas de la ciudad seleccionada
        outbox.setContent("Request join session " + sessionKey + " in " +  current_city);
        
        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        }

        return SelectMission();
    }
        
        
        
        
        
//      ESTO ES LO DE ALEX DEL JOINSESSION
//            var tmpProviders = DFGetAllProvidersOf(sessionAlias).get(0);
//
//            if(tmpProviders != null){
//                sessionManager = tmpProviders;
//            } else {
//                Error("No session manager found");
//                return AT_ST_FULL.Status.CHECKOUT;
//            }
//
//            var services = DFGetAllServicesProvidedBy(sessionManager);
//            for (var s : services) {
//                if (s.startsWith("SESSION::")) {
//                    sessionKey = s;
//                    break;
//                }
//            }
    
    
    @Override
    public Status SelectMission(){
        controller = LARVAblockingReceive();
        if(controller.getPerformative() == ACLMessage.REQUEST){
            this.getEnvironment().makeCurrentMission(controller.getContent());
            this.MyReadPerceptions();
            resetAutoNAV();
            this.getEnvironment().resetCourse();
            return Status.SOLVEPROBLEM;
        } else if (controller.getPerformative() == ACLMessage.CANCEL){
            return Status.CHECKOUT;
        } else {
            return Status.CHECKOUT;
        }
    }
}

