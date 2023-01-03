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
    String sessionAlias = "milanesa2";
    ACLMessage controller;
    boolean goalCompleted = false;
    int auxPayload;
    String allMissions, boss;
    @Override
    public void setup() {
        this.defSessionAlias(sessionAlias);

        super.setup();
    }
    
    // Añadido nuevo estado SELECTGOAL
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
                myStatus = SelectMission();
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
    public AT_ST_FULL.Status MovementGoal(String city){
        behaviour = AgPlan(E, A);
        // Si se ha completado el plan se avanza de ojetivo
        Info("Current goal: " + current_goal[0] + " "+ current_goal[1]);
        if (behaviour == null || behaviour.isEmpty()) {
            if ("MOVEIN".equals(current_goal[0])){
                if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                    Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!!!!!!!!");
                    informBossGoal(this.getEnvironment().getCurrentGoal());
                    this.getEnvironment().setNextGoal();
                    return Status.SOLVEPROBLEM;
                }
	    } else { // "MOVEBY <droidship>".equals(current_goal)
	    	if (getEnvironment().getCurrentCity().equals(city)){
                    Info("I am in "+city);
                    Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                    informBossGoal(this.getEnvironment().getCurrentGoal());
                    this.getEnvironment().setNextGoal();
                    return Status.SOLVEPROBLEM;
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
    
    // Cambio sustancial: ahora al solucionar un goal no volvemos al SOLVEPROBLEM
    // si no que vamos al SELECTGOAL para iterar al siguiente GOAL
    // o pedirselo al SSD en caso de que no tengamos GOAL.
    
    // Algunos goals como CAPTURE han sido divididos en varios goals: 
    // REQUEST MTT, CAPTURE, CANCEL, por lo que la funcionalidad del LAB2
    // se ha visto dividida en varias funciones
    @Override
    public AT_ST_FULL.Status MySolveProblem(){
        if (getEnvironment().getCurrentMission().isOver()){
            Info("The problem is over");
            Message("The mission has been solved");
            return informBossMission();
        }
        
        // Obtenemos el objetivo actual. Los objetivos van separados por espacios
        // por lo que los splitteamos por espacios
        current_goal = getEnvironment().getCurrentGoal().split(" ");
        
        Info("Current goal: "+current_goal[0]);

        // Si el objetivo es movernos, utilizamos en asistente de LARVA
        // para desplazarnos y se realiza el algoritmo de movimiento
        if (current_goal[0].equals("MOVEIN")){
            if(current_goal.length != 2){
                current_goal [1] = current_goal[1].concat(" " + current_goal[2]);
            }
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
                informBossGoal(this.getEnvironment().getCurrentGoal());
                this.getEnvironment().setNextGoal();
                return Status.SOLVEPROBLEM;
            }  
        // Si el objetivo es listar, obtenemos la lista de personas dado un tipo
        // y se avanza al siguiente objetivo
        } else if (current_goal[0].equals("LIST")){
            doQueryPeople(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " " + current_goal[2] + " has been solved!");
            informBossGoal(this.getEnvironment().getCurrentGoal());
            this.getEnvironment().setNextGoal();        
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("REPORT")){
            doReportTypeGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            informBossGoal(this.getEnvironment().getCurrentGoal());
            this.getEnvironment().setNextGoal();        
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("REQUEST")){
            doRequestGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            informBossGoal(this.getEnvironment().getCurrentGoal());
            this.getEnvironment().setNextGoal();        
            return Status.SOLVEPROBLEM;        
        } else if (current_goal[0].equals("CAPTURE")){
            doCapture(current_goal[1], current_goal[2]);
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
            auxPayload = Integer.parseInt(current_goal[1]);
            for(int i=0; i<auxPayload; i++)
                informBossGoal(this.getEnvironment().getCurrentGoal());
            this.getEnvironment().setNextGoal();        
            return Status.SOLVEPROBLEM;           
        } else if (current_goal[0].equals("CANCEL")){
            doGoalCancel("MTT");
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
            informBossGoal(this.getEnvironment().getCurrentGoal());
            this.getEnvironment().setNextGoal();        
            return Status.SOLVEPROBLEM;
        
        } else if (current_goal[0].equals("MOVEBY")){
            if (destCity.equals("")){
                destCity = getMoveByCity(current_goal[1]);
                return MovementGoal(destCity);
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                goalCompleted = true;
                return Status.SOLVEPROBLEM;
            }
        } else if (current_goal[0].equals("TRANSFER")){
            Info("Estoy en el transfer");
            System.out.println(getEnvironment().getCurrentGoal());
            // No es necesario movernos a la ciudad de DEST ya que el goal
            // anterior es precisamente MOVEIN <ciudad de DEST>
            
            //destCity = getMoveByCity(current_goal[1]);
            //Info("Moving to "+ destCity);
            //MovementGoal(destCity);
            
            doTransfer();
            
            Info("Goal " + this.getEnvironment().getCurrentGoal() + " has been solved!");
                  
            for(int i=0; i<auxPayload; i++)
                informBossGoal(this.getEnvironment().getCurrentGoal());
                    
            this.getEnvironment().setNextGoal();        
            return Status.SOLVEPROBLEM;            
        }
        
        return Status.EXIT;
    } 
    
    public Status doTransfer(){
        Info("Getting DEST name and position ...");
        
        // Fragmento de código del getMoveByCity, no es necesario la función completa
        ArrayList<String> destProviders = this.DFGetAllProvidersOf("TYPE DEST");
        for(String provider: destProviders){
            if(this.DFHasService(provider, sessionKey)){
                destProvider = provider;
            }
        }
        
        int peopleToTransfer = getEnvironment().getPayload();
        Info("We are Transfering " + peopleToTransfer );
        
        // Al primer if únicamente se accede al transferir el primer Jedi
        // (en principio) y sirve para configurar el mensaje. Después, vamos
        // transfiriendo los Jedis uno a uno cambiando el contenido del 
        // mensaje en función del nombre del Jedi correspondiente
        int i = 0;
        while(i < peopleToTransfer){
            if (dest == null) {
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(destProvider, AID.ISLOCALNAME));
            } else { // Else follow the dialogue
                outbox = dest.createReply();
            } 
               
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("TRANSFER " + peopleNames[i]);
            outbox.setProtocol("DROIDSHIP");
            outbox.setConversationId(sessionKey);
            outbox.setReplyWith(peopleNames[i]);
            this.LARVAsend(outbox);
            dest = LARVAblockingReceive();

            Info("================\n" + dest.getContent());
            if (dest.getPerformative()==ACLMessage.INFORM){
                    Info("Jedi named [" + peopleNames[i] + "] has been transfered");
                    i += 1;
            }
        }

        return myStatus;
    }
    
    /**
     * Selecciona el siguiente Goal preguntándole al SSD en caso de que 
     * no tengamos
     * @author David Correa
     * 
     */
    public Status MySelectGoal() {
        Info("Selecting goal...");
        if(getEnvironment().getCurrentMission()==null){
            Info("No current mission, selecting...");
            
            return this.SelectMission();
        }
        else{
            Info("Setting next goal...");
            this.getEnvironment().setNextGoal();
            if (this.getEnvironment().getCurrentMission().isOver()) {
                Info("Mission is over, selecting...");
                return this.SelectMission();
            }
        }

        return Status.SOLVEPROBLEM;

    }
    
    // Override por el JOINSESSION, antes íbamos a OPENPROBLEM
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
        // Como ITTs siempre aparecemos en Whitehorse
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
        this.DFAddMyServices(new String[]{"TYPE ITT", "TEAM " + sessionAlias});
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        }
        
        outbox = session.createReply();
        outbox = session.createReply();
        outbox.setContent("Query missions session " + sessionKey);
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());
          
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
    
    // Función proporcionada por el profe, obtiene las misiones
    // del SSD
    @Override
    public Status SelectMission(){
        controller = LARVAblockingReceive();
        if(controller.getPerformative() == ACLMessage.REQUEST){
            boss = controller.getSender().getLocalName();
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
    
    // Solicita a un DROIDSHIP que venga a nuestra posición
    // Es similar a cuando en LAB2 pedíamos al MTT que viniera a ayudarnos
    public Status doRequestGoal(String type) {
        int numMessage = 0;
        
        ArrayList<String> globalProviders = this.DFGetAllProvidersOf("TYPE "+type);
        ArrayList<String> sessionProviders = new ArrayList<String>();
        for(var provider : globalProviders) {
            if (this.DFHasService(provider, sessionKey)) {
                sessionProviders.add(provider);
            }
        }
   
        
        boolean mttFound = false;
        while (!mttFound){
            for(String provider: sessionProviders){
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setProtocol("DROIDSHIP");
                outbox.setConversationId(sessionKey);
                outbox.setContent("BACKUP");
                outbox.setReplyWith(String.valueOf(numMessage));
                numMessage ++;
                this.LARVAsend(outbox);

                mtt = LARVAblockingReceive();
                if (mtt.getPerformative()==ACLMessage.AGREE){
                    mttFound = true;
                    break;
                }else if (mtt.getPerformative()==ACLMessage.REFUSE){
                        continue;
                        //mtt = LARVAblockingReceive();
                }
            }
        }

        boolean backupHasArrived = false;
        while(!backupHasArrived){
            mtt = LARVAblockingReceive();
            if(mtt.getPerformative() == ACLMessage.INFORM)
                backupHasArrived = true; 
        }
        
        return Status.SOLVEPROBLEM;
    }
    
    // Función modificada, se han extraido fragmentos de la función del padre
    @Override
    protected AT_ST_FULL.Status doCapture(String  nCaptures, String type){
        int i = 0;
        int numCaptures = Integer.parseInt(nCaptures);
        Info("Capturing " + nCaptures + " people " + type);
        
        // Hasta este punto la función es calcada a void EnergyRecharge().
        // Ahora preguntamos al SM por los nombres de los Jedis y los vamos 
        // capturando uno a uno, hasta que hemos llegado al número de capturas
        // solicitadas
        peopleNames = queryPeopleNames(type);
        var tmpNames = new ArrayList<String>();
        while (i < numCaptures){
            outbox = session.createReply();
            outbox.setContent("Request capture " + getEnvironment().getPeople()[i] + " session " + sessionKey);
            tmpNames.add(getEnvironment().getPeople()[i]);
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setConversationId(sessionKey);
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
            
            if(session.getPerformative() == ACLMessage.INFORM)
                ++i;
        }
        peopleNamesToTransfer = tmpNames.toArray(new String[0]);

        // No liberamos el MTT en esta función, si no en otra ya que es un
        // goal independiente

        return Status.SELECTGOAL;
    }

    // Manda el CANCEL al MTT que ha venido a ayudarnos
    public Status doGoalCancel(String type) {
        if (type.equals("MTT")) {
            outbox = mtt.createReply();
            outbox.setPerformative(ACLMessage.CANCEL);
            outbox.setContent("");
            outbox.setProtocol("DROIDSHIP");
            outbox.setConversationId(sessionKey);
            this.LARVAsend(outbox);
        }
        
        return Status.SELECTGOAL;
    }
    
    // Informa al SSD de los GOALS conseguidos, funcionalidad opcional
    public void informBossGoal(String goalName){
        outbox = controller.createReply();

        if (goalName.startsWith("TRANSFER")){
            outbox.setContent("TRANSFER");
        }
        else
            outbox.setContent(goalName);
        outbox.setPerformative(ACLMessage.INFORM_REF);
        outbox.setConversationId(sessionAlias);

        this.LARVAsend(outbox);
    }
    
    public Status informBossMission(){
        outbox = controller.createReply();        
        outbox.setContent(this.getEnvironment().getCurrentMission().getName());
        outbox.setPerformative(ACLMessage.INFORM);
        
        this.LARVAsend(outbox);
        return Status.SELECTGOAL;
    }
}

