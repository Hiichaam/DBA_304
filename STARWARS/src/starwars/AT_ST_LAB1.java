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
import agents.MTT;
import agents.YV;
import ai.Choice;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
/**
 *
 * @author dcardenas11
 */
public class AT_ST_LAB1 extends AT_ST_FULL{
    String[] cities,
             current_goal;
    String current_city;
    Point3D city_coord;
    Map<String, String> reportMap = new HashMap<String, String>();
    
    // Igual que en los agentes anteriores, excepto:
    // CAMBIO EN EL SESION ALIAS
    // Se añade en el request open problem el sessionAlias para poder
    // hacer sesiones compartidas, en este caso con los NPCs
    @Override
    public Status MyOpenProblem() {

        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        
        // Seleccionar el problema, en este caso son los APR, NOT, SOB, MAT
        // de la práctica
        problem = this.inputSelect("Please select problem to solve", problems, problem);
        if (problem == null) {
            return Status.CHECKOUT;
        }
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        
        // Cambio añadido
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
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
            return Status.JOINSESSION;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    @Override
    public Status MyJoinSession(){
        outbox = session.createReply();
        
        // Pedir las ciudades disponibles
        outbox.setContent("Query cities session " + sessionKey);
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
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        if (!session.getContent().startsWith("Confirm")){
            Error("Could not join session " + sessionKey + " due " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        // Ejecutar los NPCs
        this.doPrepareNPC(1, DEST.class);
        //this.doPrepareNPC(2, BB1F.class);
        //this.doPrepareNPC(0, YV.class);
        //this.doPrepareNPC(0, MTT.class);

        outbox = session.createReply();
        
        // Obtenemos las misiones del problema abierto.
        // IMPORTANTE: tiene que coincidir con el problema abierto
        outbox.setContent("Query missions session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        
        // Antes de seleccionar la misión es necesario actualizar las percepciones
        this.MyReadPerceptions();
        return SelectMission();
    }
    
    // Elige una misión:
    // Si solo hay una elige dicha misión
    // Si hay varias le da la opción al usuario de elegir con un desplegable
    @Override
    protected String chooseMission(){
        Info("Choosing a mission");
        String m = "";
        if (getEnvironment().getAllMissions().length == 1){
            m = getEnvironment().getAllMissions()[0];
        } else {
            m = this.inputSelect("Please choose a mission",getEnvironment().getAllMissions(),"");
        }
        Info("Selected mission " + m);
        return m;
    }
    
    // Selecciona una misión. Llama a chooseMission
    public Status SelectMission(){
        String m = chooseMission();
        if (m == null){
            return Status.CLOSEPROBLEM;
        }
        getEnvironment().setCurrentMission(m);
        return Status.SOLVEPROBLEM;
    }
    
    // Obtiene las personas dado un tipo. Este método es necesario para
    // hacer el LIST de personas
    /**
    *
    * @author Dani Cardenas
    * @author Alex Herrera
    * @author David Correa
    */
    protected Status doQueryPeople(String type){
        Info("Querying people " + type);
        outbox = session.createReply();
        outbox.setContent("Query " + type.toUpperCase() + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        Message("Found " + getEnvironment().getPeople().length + " " + type + " in " + getEnvironment().getCurrentCity());
        if(reportMap.containsKey(getEnvironment().getCurrentCity())){
            String tmp = reportMap.get(getEnvironment().getCurrentCity());
            tmp += " " + type + " " + getEnvironment().getPeople().length;
            reportMap.put(getEnvironment().getCurrentCity(), tmp);
        } else {
            reportMap.put(getEnvironment().getCurrentCity(), new String(type + " " + getEnvironment().getPeople().length));
        }
        return myStatus;
    }
    
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
            outbox = session.createReply();
            outbox.setContent("Request course in " + current_goal[1] + " session " + sessionKey);
            this.LARVAsend(outbox);
            session = this.LARVAblockingReceive();
            getEnvironment().setExternalPerceptions(session.getContent());
            return MovementGoal();
            
        // Si el objetivo es listar, obtenemos la lista de personas dado un tipo
        // y se avanza al siguiente objetivo
        } else if (current_goal[0].equals("LIST")){
            doQueryPeople(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " " + current_goal[2] + " has been solved!");
            getEnvironment().getCurrentMission().nextGoal();
            return Status.SOLVEPROBLEM;
        
        } else {
            doReportTypeGoal(current_goal[1]);
            Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
            getEnvironment().getCurrentMission().nextGoal();
            return Status.SOLVEPROBLEM;
        }
    }
    /**
    *
    * @author Dani Cardenas
    * @author Alex Herrera
    * @author David Correa
    */
    protected Status doReportTypeGoal(String type){
        String Report = "REPORT;";
        Iterator it = reportMap.keySet().iterator();
        
        boolean first = true;
        for (var entry : reportMap.entrySet()) {
            if(first){
                Report += entry.getKey() + " " + entry.getValue();
                first = false;
            } else
                Report += ";" + entry.getKey() + " " + entry.getValue();
        }
        Report += ";";

        //Info("REPORT:" + Report);
        //this.getEnvironment().
        Info("Reporting " + type);
        DroidShip.Debug();
        ArrayList<String> providers = this.DFGetAllProvidersOf("TYPE " + type.toUpperCase());
        
        String receiverAgent = "";
        for(String provider: providers){
            if(this.DFHasService(provider, sessionKey)){
                receiverAgent = provider;
            }
        }
        //ArrayList<String> services = this.DFGetServiceList();
        //ArrayList<String> providers = this.DFGetProviderList();
        
        
        Info("Agent type " + type + ": " + receiverAgent + " found!");
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(receiverAgent, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.INFORM_REF);
        outbox.setProtocol("DROIDSHIP");
        outbox.setContent(Report);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        
        if (session.getContent().split(" ")[0].toUpperCase().equals("CONFIRM")){
            Info("Agent " + receiverAgent + " has sent " + session.getContent());
            getEnvironment().getCurrentMission().nextGoal();
            return Status.SOLVEPROBLEM;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    /**
    *
    * @author David Correa
    */
    // Cambio en la inteligencia del agente:
    // - Si el objetivo está a la izquierda, se dirige a la izquierda
    // - Si el objetivo está a la derecha, se dirige a la derecha
    @Override
    public double goAvoid(Environment E, Choice a){
        // Las siguientes lineas de codigo hacen que en el Honor1 el agente se de un buen paseo -> Decidimos dejar el agente con tendencia de giro a la IZDA
        
//        if (E.isTargetLeft() || E.isTargetFrontLeft()){
//            if (a.getName().equals("LEFT")) {
//                nextWhichwall = "RIGHT";
//                nextdistance = E.getDistance();
//                nextPoint = E.getGPS();
//                return Choice.ANY_VALUE;
//            }
//        }
//        else if(E.isTargetRight() || E.isTargetFrontRight()){
//            if (a.getName().equals("RIGHT")) {
//                nextWhichwall = "LEFT";
//                nextdistance = E.getDistance();
//                nextPoint = E.getGPS();
//                return Choice.ANY_VALUE;
//            }
//        }
//        return Choice.MAX_UTILITY;

        if (a.getName().equals("LEFT")) {
            nextWhichwall = "RIGHT";
            nextdistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }
    // Algoritmo del movimiento del agente, es llamado por el bloque de 
    // código asociado a la accion MOVE
    // Función iterativa
    public Status MovementGoal(){
        // if (G(E)) {
        //    Info("The problem is over");
        //    this.Message("The problem " + problem + " has been solved");
        //    return Status.CLOSEPROBLEM;
        //}
        behaviour = AgPlan(E, A);
        // Si se ha completado el plan se avanza de objetivo
        if (behaviour == null || behaviour.isEmpty()) {
            if (getEnvironment().getCurrentCity().equals(current_goal[1])){
                Info("Goal " + current_goal[0] + " " + current_goal[1] + " has been solved!");
                getEnvironment().getCurrentMission().nextGoal();
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
                if (!Ve(E)) {
                    this.Error("The agent is not alive: " + E.getStatus());
                    return Status.CLOSEPROBLEM;
                }
            }
            this.MyReadPerceptions();
            return MovementGoal();   // función iterativa
        }
    }
    
    // Antes de cerrar el problema destruimos los NPCs creados previamente
    @Override
    public Status MyCloseProblem(){
        this.doDestroyNPC();
        return super.MyCloseProblem();
    }
}