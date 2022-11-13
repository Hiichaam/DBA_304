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
    
    @Override
    public Status MySolveProblem(){
        if (getEnvironment().getCurrentMission().isOver()){
            Info("The problem is over");
            Message("The problem " + problem + " has been solved");
            return Status.CLOSEPROBLEM;
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
                this.LARVAsend(outbox);
                session = this.LARVAblockingReceive();
                getEnvironment().setExternalPerceptions(session.getContent());
                return MovementGoal();
            } else {
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                //getEnvironment().getCurrentMission().nextGoal();
                getEnvironment().setNextGoal();
                return Status.SOLVEPROBLEM;
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
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("CAPTURE")){
            doCapture(current_goal[1], current_goal[2]);
            getEnvironment().setNextGoal();
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("MOVEBY")){
            return Status.SOLVEPROBLEM;
        } else if (current_goal[0].equals("TRANSFER")){
            return Status.SOLVEPROBLEM;            
        }
        
        return Status.EXIT;
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
    
    public Status MovementGoal(){
        behaviour = AgPlan(E, A);
        // Si se ha completado el plan se avanza de objetivo
        if (behaviour == null || behaviour.isEmpty()) {
            if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                //getEnvironment().getCurrentMission().nextGoal();
                getEnvironment().setNextGoal();
                return Status.SOLVEPROBLEM;
            }
            Alert("Found no plan to execute");
            return Status.CLOSEPROBLEM;
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
                    return Status.CLOSEPROBLEM;
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
    
    protected Status doCapture(String  nCaptures, String type){
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
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();
        
        }
        
        
        return myStatus;
        
        
             
    }

    
    
    
}