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
    String sessionAlias = "milanesa";
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
    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        //this.loadMyPassport("config/DANIEL_CARDENAS_CASTRO.passport");
        
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.JOINSESSION;
    }

    @Override
    public AT_ST_FULL.Status MyJoinSession(){
        
        // Obtenemos el sessionManager buscando por la lista de todos los
        // session managers
        ArrayList<String> allSM = this.DFGetAllProvidersOf("SESSION MANAGER");
        
        for (String sm : allSM){
            if (this.DFHasService(sm, sessionAlias)){
                //session.addReceiver(new AID(sm, AID.ISLOCALNAME));
                sessionManager = sm;
                break;
            }
        }
        System.out.println("Session manager: "+sessionManager);
        // Otra alternativa
        // int i=0;
        // do{
        //   sessionManager = allSM.get(i);
        //   i++;
        //}while(!this.DFHasService(sessionManager, sessionAlias));
            
        // Hasta este punto tenemos localizado nuestro session manager gracias
        // al session alias. Tendremos algo como
        // |S.M. XXX      |🟢|ADMIN     |SESSION MANAGER|
        // SESSION MANAGER XXXX |PROBLEM XXXX|XXXX  |SESSION::sessionKey|
        // Por tanto, tenemos que obtener los servicios que tiene nuestro session
        // manager y filtrar por el que empieza por SESSION:: para obtener el sessionKey
        
        ArrayList<String> allServices = this.DFGetAllServicesProvidedBy(sessionManager);
        for (String Service : allServices){
            if (Service.startsWith("SESSION::")){
                sessionKey = Service;
                break;
            }
        }
        System.out.println("Session key"+sessionKey);
                
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

