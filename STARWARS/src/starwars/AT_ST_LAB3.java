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

    @Override
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
            case SELECTGOAL:
                myStatus = MySelectGoal();
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
    public AT_ST_FULL.Status MovementGoal(String city){
        behaviour = AgPlan(E, A);
        // Si se ha completado el plan se avanza de objetivo
        if (behaviour == null || behaviour.isEmpty()) {
            if ("MOVEIN".equals(current_goal[0])){
                if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                    Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                    getEnvironment().setNextGoal();
                    return AT_ST_FULL.Status.SELECTGOAL;
                }
	    } else { // "MOVEBY <droidship>".equals(current_goal)
	    	if (getEnvironment().getCurrentCity().equals(city)){
                    Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                    getEnvironment().setNextGoal();
                    return AT_ST_FULL.Status.SELECTGOAL;
                }
	    }
            Alert("Found no plan to execute");
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        // Si hay acciones por realizar del plan
        } else {// Execute
            Info("Found plan: " + behaviour.toString());
            while (!behaviour.isEmpty()) {
                a = behaviour.get(0);
                behaviour.remove(0);
                Info("Excuting " + a);
                this.MyExecuteAction(a.getName());
                if(getEnvironment().getEnergy() < EnergyLimitToAskRecharge){
                    EnergyRecharge();
                }
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return AT_ST_FULL.Status.CLOSEPROBLEM;
                }
            }
            this.MyReadPerceptions();
            return MovementGoal(city);   // función iterativa
        }
    }
    
    @Override
    public AT_ST_FULL.Status MySolveProblem(){
        if (getEnvironment().getCurrentMission().isOver()){
            Info("The problem is over");
            Message("The problem " + problem + " has been solved");
            return this.SelectMission();
        }
        
        // Obtenemos el objetivo actual. Los objetivos van separados por espacios
        // por lo que los splitteamos por espacios
        current_goal = getEnvironment().getCurrentGoal().split(" ");
  
        // Si el objetivo es movernos, utilizamos en asistente de LARVA
        // para desplazarnos y se realiza el algoritmo de movimiento
        if (current_goal[0].equals("MOVEIN")){
            if (!getEnvironment().getCurrentCity().equals(current_goal[1])){
                outbox = session.createReply();
                outbox.setContent("Request course in " + current_goal[1] + " session " + sessionKey);
                
                // Cambio performativa y conversational id
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setConversationId(sessionKey);
                
                this.LARVAsend(outbox);
                session = this.LARVAblockingReceive();
                getEnvironment().setExternalPerceptions(session.getContent());
                return MovementGoal("");
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                return Status.SELECTGOAL;
            }  
        // Si el objetivo es listar, obtenemos la lista de personas dado un tipo
        // y se avanza al siguiente objetivo
        } else if (current_goal[0].equals("LIST")){
            doQueryPeople(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " " + current_goal[2] + " has been solved!");
            
            return Status.SELECTGOAL;
        } else if (current_goal[0].equals("REPORT")){
            doReportTypeGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            return Status.SELECTGOAL;
        } else if (current_goal[0].equals("CAPTURE")){
            doCapture(current_goal[1], current_goal[2]);
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
            return Status.SELECTGOAL;
        } else if (current_goal[0].equals("MOVEBY")){
            if (destCity.equals("")){
                destCity = getMoveByCity(current_goal[1]);
                return MovementGoal(destCity);
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                return Status.SELECTGOAL;
            }
        } else if (current_goal[0].equals("TRANSFER")){
            Info("Estoy en el transfer");
            System.out.println(getEnvironment().getCurrentGoal());
            doTransfer();
            
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
            return Status.SELECTGOAL;            
        }
        
        return Status.EXIT;
    } 
    
    public Status MySelectGoal() {
        Info("ESTOY EN EL SELECT GOALLLL");
        if(getEnvironment().getCurrentMission()==null){
            Info("No current mission, selecting...");
            return this.SelectMission();
        }
        else{
            Info("Setting next goal...");
            getEnvironment().setNextGoal();

            //En caso de haber acabado nuestra misión, esperamos indicaciones del SSD
            if (getEnvironment().getCurrentMission().isOver()) {
                Info("Mission is over, selecting...");
                return this.SelectMission();
            }
        }

        return Status.SOLVEPROBLEM;

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
        System.out.println("Session key: "+sessionKey);
                
        current_city = "Whitehorse";
        
        outbox = new ACLMessage();
        outbox.setSender(this.getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        
        // Iniciamos sesión en las coordenadas de la ciudad seleccionada
        outbox.setContent("Request join session " + sessionKey + " in " +  current_city);
        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        this.DFAddMyServices(new String[]{"TEAM " + sessionAlias});
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        }
        
        outbox = session.createReply();
          
        // Antes de seleccionar la misión es necesario actualizar las percepciones
        this.MyReadPerceptions();

        return Status.SELECTGOAL;
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
            Info("Cancel received");
            return Status.CHECKOUT;
        } else {
            return Status.CHECKOUT;
        }
    }
    
    public void informBoss(){
        controller.createReply();
        controller.setContent(E.getCurrentGoal());
        controller.setConversationId(sessionKey);
        controller.setPerformative(ACLMessage.INFORM_REF);
        this.LARVAsend(outbox);
    }
}

