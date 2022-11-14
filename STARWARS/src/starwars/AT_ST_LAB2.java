/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package starwars;

import Environment.Environment;
import agents.BB1F;
import agents.DEST;
import agents.DroidShip;
import agents.LARVAFirstAgent;
import agents.MTT;
import agents.YV;
import ai.Choice;
import geometry.Point3D;
import glossary.Sensors;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
/**
 *
 * @author dcardenas11
 */
public class AT_ST_LAB2 extends AT_ST_LAB1{
    
    // Igual que en los agentes anteriores, excepto:
    // Cambio en las performativas
    @Override
    public AT_ST_FULL.Status MyOpenProblem() {

        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return AT_ST_FULL.Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        
        // Seleccionar el problema, en este caso son los APR, NOT, SOB, MAT
        // de la práctica
        problem = this.inputSelect("Please select problem to solve", problems, problem);
        if (problem == null) {
            return AT_ST_FULL.Status.CHECKOUT;
        }
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        
        // Cambio añadido
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
        // Cambio performativa
        outbox.setPerformative(ACLMessage.REQUEST);
        
        this.LARVAsend(outbox);
        Info("opening problem " + problem + " to " + problemManager);
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return AT_ST_FULL.Status.JOINSESSION;
        } else {
            Error(content);
            return AT_ST_FULL.Status.CHECKOUT;
        }
    }
    
    // Igual que en los agentes anteriores, excepto:
    // Cambio en las performativas
    @Override
    public AT_ST_FULL.Status MyJoinSession(){
        outbox = session.createReply();
        
        // Pedir las ciudades disponibles
        outbox.setContent("Query cities session " + sessionKey);
        
        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        this.getEnvironment().setExternalPerceptions(session.getContent());
        
        // Guardamos la lista de ciudades proporcionada
        cities = getEnvironment().getCityList();
        
        // Preguntamos al usuario en qué ciudad quiere aparecer
        current_city = this.inputSelect("Please select city to start", cities, "");
        city_coord = getEnvironment().getCityPosition(current_city);
        
        this.resetAutoNAV();
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        
        // Iniciamos sesión en las coordenadas de la ciudad seleccionada
        outbox.setContent("Request join session " + sessionKey + " at " + city_coord.getXInt() + " " + city_coord.getYInt());
        
        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setConversationId(sessionKey);
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return AT_ST_FULL.Status.CLOSEPROBLEM;
        }
        
        // Ejecutar los NPCs
        this.doPrepareNPC(1, DEST.class);
        this.doPrepareNPC(4, BB1F.class);//n es el numero de veces que se lanza
        //this.doPrepareNPC(0, YV.class);
        //this.doPrepareNPC(0, MTT.class);

        outbox = session.createReply();
        
        // Obtenemos las misiones del problema abierto.
        // IMPORTANTE: tiene que coincidir con el problema abierto
        outbox.setContent("Query missions session " + sessionKey);
        
        // Cambio performativa y conversational id
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setConversationId(sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        
        // Antes de seleccionar la misión es necesario actualizar las percepciones
        this.MyReadPerceptions();
        return SelectMission();
    }
    
    
    
    @Override
    public AT_ST_FULL.Status MySolveProblem(){
        if (getEnvironment().getCurrentMission().isOver()){
            Info("The problem is over");
            Message("The problem " + problem + " has been solved");
            return AT_ST_FULL.Status.CLOSEPROBLEM;
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
                return MovementGoal();
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                //getEnvironment().getCurrentMission().nextGoal();
                getEnvironment().setNextGoal();
                return AT_ST_FULL.Status.SOLVEPROBLEM;
            }  
        // Si el objetivo es listar, obtenemos la lista de personas dado un tipo
        // y se avanza al siguiente objetivo
        } /*else if (current_goal[0].equals("LIST")){
            doQueryPeople(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " " + current_goal[2] + " has been solved!");
            //getEnvironment().getCurrentMission().nextGoal();
            getEnvironment().setNextGoal();
            return Status.SOLVEPROBLEM;
        
        }*/ else if (current_goal[0].equals("REPORT")){
            doReportTypeGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            //getEnvironment().getCurrentMission().nextGoal();
            getEnvironment().setNextGoal();
            return AT_ST_FULL.Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("CAPTURE")){
            doCapture(current_goal[1], current_goal[2]);
            getEnvironment().setNextGoal();
            return AT_ST_FULL.Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("MOVEBY")){
            return AT_ST_FULL.Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("TRANSFER")){
            return AT_ST_FULL.Status.SOLVEPROBLEM;            
        }
        
        return AT_ST_FULL.Status.EXIT;
    }
    
    // Antes de cerrar el problema destruimos los NPCs creados previamente
    // Añadido performativas
    @Override
    public Status MyCloseProblem(){
        this.doDestroyNPC();
        
        // AFter all, it is mandatory closing the problem
        // by replying to the backup message
        Info("Plan = " + preplan);
        outbox = open.createReply();
        
        outbox.setContent("Cancel session " + sessionKey);
        outbox.setConversationId(sessionKey);
        outbox.setPerformative(ACLMessage.CANCEL);
        
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }
    
    @Override
    public ACLMessage LARVAblockingReceive() {
        boolean exit = false;
        ACLMessage res = null;
        while (!exit) {
            res = super.LARVAblockingReceive();
            if(res.getContent().equals("TRANSPONDER") && res.getPerformative() == ACLMessage.QUERY_REF) {
                outbox = res.createReply();
                outbox.setPerformative(ACLMessage.INFORM);
                outbox.setContent(this.Transponder());
                LARVAsend(outbox);
            } else{
                exit = true;
            }
        }
        return res;
    }
    
    public void EnergyRecharge() {
        
        Info("Recharging...");
        ArrayList<String> providers = this.DFGetAllProvidersOf("TYPE BB1F");
        ArrayList<Integer> distances = new ArrayList<Integer>();
        
        //Esto es para ordenar los providers por cercania
        for(var provider : providers){
            outbox = new ACLMessage();
            outbox.setSender(getAID());
            outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
            outbox.setPerformative(ACLMessage.QUERY_REF);
            outbox.setContent("TRANSPONDER");
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
            
            contentTokens = session.getContent().split(",");
            String coordinates = contentTokens[4];
            coordinates = coordinates.replace(coordinates, "");
            
            var location = new Point3D(coordinates);
            distances.add(E.getGPS().gridDistanceTo(location));
        }
        //A ver si a alguien se le ocurre una forma mas simple de ordenar provider por el vector distances, esto me parece mucho lio
        Map<Integer, String> map = new HashMap<Integer, String>();
        for(Integer i = 0; i < providers.size(); i++) {
          map.put(distances.get(i),providers.get(i)); 
        }

        Collections.sort(distances);
        providers.clear();

        for(Integer i = 0; i < map.size(); i++) {
          providers.add(map.get(distances.get(i)));
        }
        
        boolean rechargeFound = false;
        while (!rechargeFound){
            for(String provider: providers){
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setProtocol("DROIDSHIP");
                outbox.setConversationId(sessionKey);
                outbox.setContent("REFILL");
                this.LARVAsend(outbox);
                session = LARVAblockingReceive();
                if (session.getContent().split(" ")[0].toUpperCase().equals("AGREE")){
                    rechargeFound = true;
                    break;
                }  
            }
        }
        
        //Esta parte de larva no esta documentada y no tengo ni idea de como usar esto, creo que es algo asi, pero no puedo ver el constructor de MatchExpression 
        /*var t = new ACLMessage();
        t.setPerformative(ACLMessage.INFORM);
        var template = new MessageTemplate(new MatchExpression(t));
        LARVAblockingReceive(template);*/
        
        
        //Habria que hacerlo con lo que hay arriba comentado, esto es una alternativa, ya vemos luego con cual nos quedamos.
        boolean rechaged = false;
        while(!rechaged){
            session = LARVAblockingReceive();
            if(session.getPerformative() == ACLMessage.INFORM)
                rechaged = true;
        }
        
        this.MyReadPerceptions();
        Info("Rechage completed");
    }
    
    public AT_ST_FULL.Status MovementGoal(){
        behaviour = AgPlan(E, A);
        // Si se ha completado el plan se avanza de objetivo
        if (behaviour == null || behaviour.isEmpty()) {
            if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                //getEnvironment().getCurrentMission().nextGoal();
                getEnvironment().setNextGoal();
                return AT_ST_FULL.Status.SOLVEPROBLEM;
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
                if(E.isEnergyExhausted())
                    EnergyRecharge();
                
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return AT_ST_FULL.Status.CLOSEPROBLEM;
                }
            }
            this.MyReadPerceptions();
            return MovementGoal();   // función iterativa
        }
    }
    
    /**
    *
    * @author Hicham
    */
    
    protected AT_ST_FULL.Status doCapture(String  nCaptures, String type){
        int numCaptures = Integer.parseInt(nCaptures);
        Info("Capturing" + nCaptures + " people " + type);
        
        ArrayList<String> providers = this.DFGetAllProvidersOf("TYPE MTT");
        ArrayList<Integer> distances = new ArrayList<Integer>();
        
        //Esto es para ordenar los providers por cercania
        for(var provider : providers){
            outbox = new ACLMessage();
            outbox.setSender(getAID());
            outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
            outbox.setPerformative(ACLMessage.QUERY_REF);
            outbox.setContent("TRANSPONDER");
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
            
            contentTokens = session.getContent().split(",");
            String coordinates = contentTokens[4];
            coordinates = coordinates.replace(coordinates, "");
            
            var location = new Point3D(coordinates);
            distances.add(E.getGPS().gridDistanceTo(location));
        }
        
        Map<Integer, String> map = new HashMap<Integer, String>();
        for(Integer i = 0; i < providers.size(); i++) {
          map.put(distances.get(i),providers.get(i)); 
        }

        Collections.sort(distances);
        providers.clear();

        for(Integer i = 0; i < map.size(); i++) {
          providers.add(map.get(distances.get(i)));
        }
        
        boolean mttFound = false;
        while (!mttFound){
            for(String provider: providers){
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(provider, AID.ISLOCALNAME));
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setProtocol("DROIDSHIP");
                outbox.setConversationId(sessionKey);
                outbox.setContent("BACKUP");
                this.LARVAsend(outbox);
                session = LARVAblockingReceive();
                if (session.getContent().split(" ")[0].toUpperCase().equals("AGREE")){
                    mttFound = true;
                    break;
                }  
            }
        }
        
        boolean startCaprure = false;
        while(!startCaprure){
            session = LARVAblockingReceive();
            if(session.getPerformative() == ACLMessage.INFORM)
                startCaprure = true;
            
        }
        
        for (int i = 0; i < numCaptures ; i ++){
            outbox = session.createReply();
            outbox.setContent("Request capture ");
            
            // Añadido por David. Borra el comment cuando lo veas Hicham jajaj
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setConversationId(sessionKey);
            /////////////////////////////////////////////
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
        
        }
        
        
        return myStatus;
        
        
             
    }

    
    
    
}